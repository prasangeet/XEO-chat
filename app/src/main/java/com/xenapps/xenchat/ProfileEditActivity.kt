package com.xenapps.xenchat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

class ProfileEditActivity : AppCompatActivity() {

    private lateinit var profilePicImageView: ImageView
    private lateinit var usernameEditText: EditText
    private lateinit var bioEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var cloudStorage: FirebaseStorage

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null
    private var currentAvatarUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_edit)

        inits()
        listeners()
        loadUser()
    }

    private fun inits() {
        profilePicImageView = findViewById(R.id.profilePic)
        usernameEditText = findViewById(R.id.username)
        bioEditText = findViewById(R.id.bio)
        saveButton = findViewById(R.id.continueBtn)
        progressBar = findViewById(R.id.progressBar)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        cloudStorage = FirebaseStorage.getInstance()

        updateSaveButtonState()
    }

    private fun listeners() {
        profilePicImageView.setOnClickListener {
            pickImage()
        }

        usernameEditText.doOnTextChanged { _, _, _, _ -> updateSaveButtonState() }
        bioEditText.doOnTextChanged { _, _, _, _ -> updateSaveButtonState() }

        saveButton.setOnClickListener {
            if (fieldsAreValid()) {
                progressBar.visibility = ProgressBar.VISIBLE
                if (selectedImageUri != null) {
                    uploadProfileImage()
                } else {
                    saveUserData(currentAvatarUrl)
                }
            }
        }
    }

    private fun loadUser() {
        val user = auth.currentUser
        user?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username")
                        currentAvatarUrl = document.getString("avatarUrl")
                        val bio = document.getString("bio")
                        usernameEditText.setText(username)
                        bioEditText.setText(bio)
                        currentAvatarUrl?.let { url ->
                            Glide.with(this)
                                .load(url)
                                .transform(CircleCrop())
                                .into(profilePicImageView)
                        }
                    }
                }
        }
    }

    private fun fieldsAreValid(): Boolean {
        return usernameEditText.text.isNotEmpty() && bioEditText.text.isNotEmpty()
    }

    private fun updateSaveButtonState() {
        saveButton.isEnabled = fieldsAreValid()
        saveButton.alpha = if (saveButton.isEnabled) 1.0f else 0.5f
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
                    .into(profilePicImageView)
                updateSaveButtonState()
            }
        }
    }

    private fun uploadProfileImage() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User is not logged in.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = ProgressBar.GONE
            return
        }

        selectedImageUri?.let { uri ->
            val storageRef = cloudStorage.reference.child("avatars/${user.uid}.jpg")
            val uploadTask = storageRef.putFile(uri)

            uploadTask.addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val metadata = StorageMetadata.Builder()
                        .setCustomMetadata("public", "true")
                        .build()

                    storageRef.updateMetadata(metadata)
                        .addOnSuccessListener {
                            saveUserData(downloadUri.toString())
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = ProgressBar.GONE
            }
        } ?: run {
            Toast.makeText(this, "No image selected to upload.", Toast.LENGTH_SHORT).show()
            progressBar.visibility = ProgressBar.GONE
        }
    }


    private fun saveUserData(avatarUrl: String?) {
        val user = auth.currentUser
        val uid = user?.uid
        val username = usernameEditText.text.toString()
        val bio = bioEditText.text.toString()

        if (uid != null) {
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
                    finish() // Go back to the previous activity
                }.addOnFailureListener { exception ->
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Failed to update profile: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun deleteOldImage() {
        currentAvatarUrl?.let { url ->
            val storageRef = cloudStorage.getReferenceFromUrl(url)
            storageRef.delete()
                .addOnSuccessListener {
                    // Successfully deleted old image
                }.addOnFailureListener {
                    // Failed to delete old image
                }
        }
    }
}
