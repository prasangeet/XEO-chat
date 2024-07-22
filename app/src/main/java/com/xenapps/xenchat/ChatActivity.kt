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

    private val publicKey by lazy { EncryptionUtils.getPublicKey() }
    private val privateKey by lazy { EncryptionUtils.getPrivateKey() }

    companion object {
        private const val FirestoreMaxMessageLength = 1000000 // Firestore document size limit in bytes
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        userAvatar = findViewById(R.id.userAvatar)
        userUsername = findViewById(R.id.userUsername)
        backIcon = findViewById(R.id.backIcon)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        val userUid = intent.getStringExtra("USER_UID")
        if (userUid != null) {
            chatId = getChatId(auth.currentUser!!.uid, userUid)
            messageAdapter = MessageAdapter(emptyList(), chatId)
            chatRecyclerView.adapter = messageAdapter

            loadUserData(userUid)
            loadMessages()
            setupScrollListener()
        } else {
            Toast.makeText(this, "User ID is missing", Toast.LENGTH_SHORT).show()
            finish()
        }

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
                    val isDelivered = document.getBoolean("delivered") ?: false
                    val isSeen = document.getBoolean("seen") ?: false
                    Log.d("ChatActivity", "Message: $encryptedMessage, Timestamp: $timestamp, delivered: $isDelivered, seen: $isSeen")
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

                // Update 'seen' status after messages are loaded
                markMessagesAsSeen()
            }
    }

    private var chatVisible: Boolean = false

    override fun onResume() {
        super.onResume()
        chatVisible = true
        markMessagesAsSeen()
    }

    override fun onPause() {
        super.onPause()
        chatVisible = false
    }

    private fun markMessagesAsSeen() {
        if (!chatVisible) return

        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("chats").document(chatId)
            .collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("seen", false)
            .get()
            .addOnSuccessListener { documents ->
                val visibleMessageIds = mutableListOf<String>()
                for (document in documents) {
                    val messageId = document.id
                    visibleMessageIds.add(messageId)
                    firestore.collection("chats").document(chatId)
                        .collection("messages").document(messageId)
                        .update("seen", true)
                        .addOnFailureListener { exception ->
                            Log.e("Firestore", "Failed to update message status: ${exception.message}")
                        }
                }
                Log.d("ChatActivity", "Marked messages as seen for user ID: $currentUserId")

                // Notify adapter with visible messages
                messageAdapter.markMessagesAsSeen(visibleMessageIds)
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Failed to get messages for update: ${exception.message}")
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

                if (encryptedMessage.length > FirestoreMaxMessageLength) {
                    Toast.makeText(this, "Message too long to send", Toast.LENGTH_SHORT).show()
                    return
                }

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
                    .addOnSuccessListener { documentReference ->
                        Log.d("Firestore", "Message sent with ID: ${documentReference.id}")
                        updateMessageStatus(documentReference.id)
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

    private fun updateMessageStatus(messageId: String) {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("chats").document(chatId)
            .collection("messages")
            .document(messageId)
            .update("delivered", true)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Message status updated to Delivered for ID: $messageId")
            }
            .addOnFailureListener { exception ->
                Log.e("ChatActivity", "Failed to update message status: ${exception.message}")
            }
    }

    private fun setupScrollListener() {
        chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                    val totalItemCount = layoutManager.itemCount
                    if (lastVisibleItemPosition == totalItemCount - 1) {
                        markMessagesAsSeen()
                    }
                }
            }
        })
    }

    private fun getChatId(user1: String, user2: String): String {
        return if (user1 > user2) "$user1-$user2" else "$user2-$user1"
    }
}
