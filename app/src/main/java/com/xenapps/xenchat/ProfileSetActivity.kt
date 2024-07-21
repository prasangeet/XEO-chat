package com.xenapps.xenchat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.gms.auth.api.signin.internal.Storage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class ProfileSetActivity : AppCompatActivity() {

    private lateinit var profilePic: ImageView
    private lateinit var usernameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var continueButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var cloudStorage: FirebaseStorage

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_set)

        initials()
        listeners()
        retrieveUsername()
    }

    private fun initials() {
        profilePic = findViewById(R.id.profilePic)
        usernameEditText = findViewById(R.id.username)
        bioEditText = findViewById(R.id.bio)
        continueButton = findViewById(R.id.continueBtn)
        progressBar = findViewById(R.id.progressBar)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        cloudStorage = FirebaseStorage.getInstance()

        updateContinueButtonState()
    }

    private fun listeners() {
        usernameEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }
        bioEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }

        profilePic.setOnClickListener {
            pickImage()
        }

        continueButton.setOnClickListener{
            if (fieldsAreValid()){
                progressBar.visibility = ProgressBar.VISIBLE
                uploadProfileImage()
            }
        }

    }
    private fun fieldsAreValid(): Boolean {
        return usernameEditText.text.isNotEmpty() && bioEditText.text.isNotEmpty() && selectedImageUri != null
    }

    private fun updateContinueButtonState() {
        continueButton.isEnabled = usernameEditText.text.isNotEmpty() && bioEditText.text.isNotEmpty() && selectedImageUri != null
        continueButton.alpha = if (continueButton.isEnabled) 1.0f else 0.5f
    }

    private fun retrieveUsername() {
        val user = auth.currentUser
        user?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username")
                        usernameEditText.setText(username)
                    }
                }
                .addOnFailureListener {
                    // Handle error
                }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedImageUri = uri
                Glide.with(this)
                    .load(uri)
                    .transform(CircleCrop())
                    .into(profilePic)
                updateContinueButtonState()
            }
        }
    }

    private fun uploadProfileImage(){
        val user = auth.currentUser
        val storageRef = cloudStorage.reference.child("avatars/${user?.uid}.jpg")
        selectedImageUri?.let { uri->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener {downloadUri ->
                        saveUserData(downloadUri.toString())
                    }.addOnFailureListener{
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this, "Failed to get download URL", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener { exception ->
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Failed to upload image: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveUserData(avatarUrl: String) {
        val user = auth.currentUser
        val uid = user?.uid
        val username = usernameEditText.text.toString()
        val bio = bioEditText.text.toString()

        if (uid!=null){
            val userData = hashMapOf(
                "username" to username,
                "email" to user.email,
                "avatarUrl" to avatarUrl,
                "bio" to bio
            )

            firestore.collection("users").document(uid).set(userData)
                .addOnSuccessListener {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }.addOnFailureListener{exception ->
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Failed to update profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                }

        }

    }
}
