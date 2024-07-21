package com.xenapps.xenchat.auth

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.xenapps.xenchat.MainActivity
import com.xenapps.xenchat.ProfileSetActivity
import com.xenapps.xenchat.R

class VerificationActivity : AppCompatActivity() {

    private lateinit var backBtn: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var refreshBtn: TextView
    private lateinit var resendBtn: TextView
    private lateinit var countDownText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        backBtn = findViewById(R.id.backBtn)
        progressBar = findViewById(R.id.progressBar)
        refreshBtn = findViewById(R.id.Refreshbtn)
        resendBtn = findViewById(R.id.resendBtn)
        countDownText = findViewById(R.id.countDown)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        backBtn.setOnClickListener {
            startActivity(Intent(this@VerificationActivity, SignInActivity::class.java))
            finish()
        }

        refreshBtn.setOnClickListener {
            checkEmailVerification()
        }

        resendBtn.setOnClickListener {
            resendVerificationEmail()
        }

        startCountdown()
    }

    private fun checkEmailVerification() {
        progressBar.visibility = ProgressBar.VISIBLE
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                if (user.isEmailVerified) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Email verified", Toast.LENGTH_SHORT).show()
                    saveUserData(user)
                } else {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Email not verified yet", Toast.LENGTH_SHORT).show()
                }
            } else {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Failed to check email verification", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserData(user: FirebaseUser) {
        val uid = user.uid // Get the UID
        val email = user.email
        val username = intent.getStringExtra("username")?:""
        if (email != null) {
            val userData = hashMapOf(
                "username" to username,
                "email" to email,
                "avatarUrl" to "",
                "bio" to ""
            )

            firestore.collection("users").document(uid).set(userData)
                .addOnSuccessListener {
                    checkUserProfile(uid)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User email or username is missing", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserProfile(uid: String){
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if(document!= null){
                    val username = document.getString("username")
                    val avatarUrl = document.getString("avatarUrl")
                    val bio = document.getString("bio")

                    if(username.isNullOrEmpty() || avatarUrl.isNullOrEmpty() || bio.isNullOrEmpty()){
                        startActivity(Intent(this@VerificationActivity, ProfileSetActivity::class.java))
                    }else{
                        startActivity(Intent(this@VerificationActivity, MainActivity::class.java))
                    }
                    finish()
                }else{
                    Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener{e ->
                Toast.makeText(this, "Failed to check user profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startCountdown() {
        resendBtn.isEnabled = false
        object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countDownText.text = "(${millisUntilFinished / 1000})"
            }

            override fun onFinish() {
                countDownText.text = ""
                resendBtn.isEnabled = true
            }
        }.start()
    }

    private fun resendVerificationEmail() {
        val user: FirebaseUser? = auth.currentUser
        user?.sendEmailVerification()?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show()
                startCountdown()
            } else {
                Toast.makeText(this, "Failed to send verification email", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
