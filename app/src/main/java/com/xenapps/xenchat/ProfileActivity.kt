package com.xenapps.xenchat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.xenapps.xenchat.auth.SignInActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var profilePicImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var bioTextView: TextView
    private lateinit var logOutButton: Button
    private lateinit var editProfileButton: ImageView // Added for edit profile functionality
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        inits()
        listeners()
        loadUser()
    }

    private fun inits() {
        backBtn = findViewById(R.id.goback)
        profilePicImageView = findViewById(R.id.profilePic)
        usernameTextView = findViewById(R.id.username)
        emailTextView = findViewById(R.id.email)
        bioTextView = findViewById(R.id.bio)
        logOutButton = findViewById(R.id.logOutBtn)
        editProfileButton = findViewById(R.id.editProfile) // Initialize the edit profile button
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    private fun listeners() {
        backBtn.setOnClickListener {
            finish()
        }

        logOutButton.setOnClickListener {
            auth.signOut()
            val intent = Intent(this@ProfileActivity, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        editProfileButton.setOnClickListener {
            // Navigate to ProfileEditActivity when the edit button is clicked
            val intent = Intent(this@ProfileActivity, ProfileEditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadUser() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username")
                    val avatarUrl = document.getString("avatarUrl")
                    val email = document.getString("email")
                    val bio = document.getString("bio")
                    usernameTextView.text = username ?: "N/A"
                    emailTextView.text = email ?: "N/A"
                    bioTextView.text = bio ?: "N/A"
                    avatarUrl?.let { url ->
                        Glide.with(this)
                            .load(url)
                            .transform(CircleCrop())
                            .into(profilePicImageView)
                    }
                } else {
                    Log.d("ProfileActivity", "User document does not exist.")
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load user details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
