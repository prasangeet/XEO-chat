package com.xenapps.xenchat.auth

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.xenapps.xenchat.MainActivity
import com.xenapps.xenchat.ProfileSetActivity
import com.xenapps.xenchat.R

class SignInActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var passwordToggle: ImageView
    private lateinit var continueButton: RelativeLayout
    private lateinit var signUpTextView: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        continueButton = findViewById(R.id.continueBtn)
        signUpTextView = findViewById(R.id.signUpText)
        passwordToggle = findViewById(R.id.passwordToggle)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        listeners()
    }

    private fun listeners() {
        passwordToggle.setOnClickListener {
            if (passwordEditText.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                passwordEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                passwordToggle.setImageResource(R.drawable.eye_open)
            } else {
                passwordEditText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                passwordToggle.setImageResource(R.drawable.eye_closed)
            }
            passwordEditText.setSelection(passwordEditText.text.length)
        }
        emailEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }
        passwordEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }

        continueButton.setOnClickListener {
            signInUser(emailEditText.text.toString(), passwordEditText.text.toString())
        }

        signUpTextView.setOnClickListener {
            startActivity(Intent(this@SignInActivity, SignUpActivity::class.java))
            finish()
        }
    }

    private fun updateContinueButtonState() {
        continueButton.isEnabled = emailEditText.text.isNotEmpty() && passwordEditText.text.isNotEmpty()
        continueButton.alpha = if (continueButton.isEnabled) 1.0f else 0.5f
    }

    private fun signInUser(email: String, password: String) {
        setContinueButtonEnabled(false)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        checkEmailVerification(user)
                    }
                } else {
                    // Handle sign-in failure
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    setContinueButtonEnabled(true)
                }
            }
    }

    private fun checkEmailVerification(user: FirebaseUser) {
        if (user.isEmailVerified) {
            // Email is verified, navigate to MainActivity
            navigateToNextActivity()
        } else {
            // Email is not verified, navigate to VerificationActivity
            startActivity(Intent(this@SignInActivity, VerificationActivity::class.java))
            finish()
        }
        setContinueButtonEnabled(true)
    }

    private fun navigateToNextActivity() {
        val user = auth.currentUser
        if(user!=null){
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener {document->
                    if(document!=null){
                        val username = document.getString("username")
                        val avatarUrl = document.getString("avatarUrl")
                        val bio = document.getString("bio")

                        if(username.isNullOrEmpty() || avatarUrl.isNullOrEmpty() || bio.isNullOrEmpty()){
                            startActivity(Intent(this, ProfileSetActivity::class.java))
                        }else{
                            startActivity(Intent(this, MainActivity::class.java))
                        }
                        finish()
                    }else{
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener{
                    e-> Toast.makeText(this, "Failed to check user profile: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }else{
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setContinueButtonEnabled(enabled: Boolean) {
        continueButton.isEnabled = enabled
        continueButton.alpha = if (enabled) 1.0f else 0.5f
    }
}
