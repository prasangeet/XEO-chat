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
import com.xenapps.xenchat.R

class SignUpActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var passwordToggle: ImageView
    private lateinit var continueButton: RelativeLayout
    private lateinit var signInTextView: TextView

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        usernameEditText = findViewById(R.id.username)
        emailEditText = findViewById(R.id.email)
        passwordEditText = findViewById(R.id.password)
        continueButton = findViewById(R.id.continueBtn)
        signInTextView = findViewById(R.id.signInText)
        passwordToggle = findViewById(R.id.passwordToggle)

        listeners()
    }

    private fun listeners(){
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
        usernameEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }
        emailEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }
        passwordEditText.doOnTextChanged { _, _, _, _ -> updateContinueButtonState() }

        continueButton.setOnClickListener {
            registerUser()
        }

        signInTextView.setOnClickListener {
            startActivity(Intent(this@SignUpActivity, SignInActivity::class.java))
            finish()
        }
    }

    private fun updateContinueButtonState() {
        continueButton.isEnabled = emailEditText.text.isNotEmpty() && passwordEditText.text.isNotEmpty() && usernameEditText.text.isNotEmpty()
        continueButton.alpha = if (continueButton.isEnabled) 1.0f else 0.5f
    }

    private fun registerUser() {
        val email = emailEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()
        val username = usernameEditText.text.toString().trim() // Get the username

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Email, Password, and Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Registration successful
                    val user = auth.currentUser
                    user?.sendEmailVerification()?.addOnCompleteListener { verificationTask ->
                        if (verificationTask.isSuccessful) {
                            // Email sent
                            Toast.makeText(this, "Registration successful. Verification email sent.", Toast.LENGTH_SHORT).show()

                            // Create intent to start VerificationActivity and pass username
                            val intent = Intent(this, VerificationActivity::class.java).apply {
                                putExtra("username", username)
                            }
                            startActivity(intent)
                            finish()
                        } else {
                            // Handle failure in sending verification email
                            Toast.makeText(this, "Failed to send verification email. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    // Handle registration failure
                    val errorMessage = task.exception?.message ?: "Registration failed. Please try again."
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }

}
