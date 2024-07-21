package com.xenapps.xenchat

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.xenapps.xenchat.auth.SignInActivity
import com.xenapps.xenchat.auth.VerificationActivity
import com.xenapps.xenchat.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Delay to show splash screen
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserStatus()
        }, 2000)
    }

    private fun checkUserStatus() {
        val currentUser: FirebaseUser? = auth.currentUser

        if (currentUser == null) {
            // User not signed in, navigate to SignInActivity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        } else if (!currentUser.isEmailVerified) {
            // User signed in but email not verified, navigate to VerificationActivity
            startActivity(Intent(this, VerificationActivity::class.java))
            finish()
        } else {
            // User signed in and email verified, navigate to next activity
            navigateToNextActivity()
        } // Close the SplashActivity
    }

    private fun navigateToNextActivity() {
        val user = auth.currentUser
        if (user != null) {
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val username = document.getString("username")
                        val avatarUrl = document.getString("avatarUrl")
                        val bio = document.getString("bio")

                        if (username.isNullOrEmpty() || avatarUrl.isNullOrEmpty() || bio.isNullOrEmpty()) {
                            startActivity(Intent(this, ProfileSetActivity::class.java))
                        } else {
                            startActivity(Intent(this, MainActivity::class.java))
                        }
                        finish()
                    } else {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to check user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
    }

}
