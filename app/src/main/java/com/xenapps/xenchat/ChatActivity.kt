package com.xenapps.xenchat

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.xenapps.xenchat.classes.Message
import com.xenapps.xenchat.classes.MessageAdapter
import com.xenapps.xenchat.utils.EncryptionUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var userAvatar: ImageView
    private lateinit var userUsername: TextView
    private lateinit var backIcon: ImageView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var messageAdapter: MessageAdapter

    private lateinit var chatId: String

    // Use EncryptionUtils methods to get public and private keys
    private val publicKey by lazy { EncryptionUtils.getPublicKey() }
    private val privateKey by lazy { EncryptionUtils.getPrivateKey() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Initialize views
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        userAvatar = findViewById(R.id.userAvatar)
        userUsername = findViewById(R.id.userUsername)
        backIcon = findViewById(R.id.backIcon)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Initialize RecyclerView
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        messageAdapter = MessageAdapter(emptyList())
        chatRecyclerView.adapter = messageAdapter

        // Load user data and messages
        val userUid = intent.getStringExtra("USER_UID")
        if (userUid != null) {
            chatId = getChatId(auth.currentUser!!.uid, userUid)
            loadUserData(userUid)
            loadMessages()
        } else {
            Toast.makeText(this, "User ID is missing", Toast.LENGTH_SHORT).show()
            finish() // End the activity if userUid is null
        }

        // Set up listeners
        backIcon.setOnClickListener { onBackPressed() }
        sendButton.setOnClickListener { sendMessage() }
    }

    private fun loadUserData(userUid: String) {
        firestore.collection("users").document(userUid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username")
                    val avatarUrl = document.getString("avatarUrl")
                    userUsername.text = username
                    if (avatarUrl != null) {
                        Glide.with(this)
                            .load(avatarUrl)
                            .transform(CircleCrop())
                            .into(userAvatar)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load user data: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadMessages() {
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Failed to load messages: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { document ->
                    val encryptedMessage = document.getString("message")
                    val timestamp = document.getLong("timestamp") ?: 0L
                    val isDelivered = document.getBoolean("isDelivered") ?: false
                    val isSeen = document.getBoolean("isSeen") ?: false
                    Log.d("ChatActivity", "Message: $encryptedMessage, Timestamp: $timestamp")
                    val decryptedMessage = encryptedMessage?.let {
                        try {
                            EncryptionUtils.decryptRSA(it, privateKey).also {
                                Log.d("Encryption", "Decrypted message: $it")
                            }
                        } catch (e: Exception) {
                            Log.e("Encryption", "Decryption error: ${e.message}")
                            null
                        }
                    }
                    document.toObject(Message::class.java)?.copy(
                        message = decryptedMessage ?: "",
                        timestamp = timestamp,
                        isDelivered = isDelivered,
                        isSeen = isSeen
                    )
                } ?: emptyList()

                val categorizedMessages = categorizeMessagesByDate(messages)
                messageAdapter.updateMessages(categorizedMessages)
                chatRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
            }
    }
    private fun categorizeMessagesByDate(messages: List<Message>): List<Any> {
        val categorizedList = mutableListOf<Any>()
        var lastDate: String? = null

        for (message in messages) {
            val date = formatDate(message.timestamp)
            if (date != lastDate) {
                categorizedList.add(date)
                lastDate = date
            }
            categorizedList.add(message)
        }

        return categorizedList
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun sendMessage() {
        val messageText = messageInput.text.toString()
        if (messageText.isNotBlank()) {
            try {
                val encryptedMessage = EncryptionUtils.encryptRSA(messageText, publicKey)
                Log.d("Encryption", "Original message: $messageText")
                Log.d("Encryption", "Encrypted message: $encryptedMessage")

                val message = Message(
                    senderId = auth.currentUser!!.uid,
                    receiverId = intent.getStringExtra("USER_UID") ?: "",
                    message = encryptedMessage,
                    timestamp = System.currentTimeMillis(),
                    isDelivered = false,
                    isSeen = false
                )
                firestore.collection("chats").document(chatId)
                    .collection("messages")
                    .add(message)
                    .addOnSuccessListener {
                        messageInput.text.clear()
                    }
                    .addOnFailureListener { exception ->
                        Log.e("Firestore", "Message sending failed: ${exception.message}")
                        Toast.makeText(this, "Failed to send the message. Please try again.", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Log.e("Encryption", "Encryption error: ${e.message}")
                Toast.makeText(this, "Failed to encrypt the message. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getChatId(currentUserId: String, otherUserId: String): String {
        return if (currentUserId < otherUserId) {
            "$currentUserId-$otherUserId"
        } else {
            "$otherUserId-$currentUserId"
        }
    }
}

