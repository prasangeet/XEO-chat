package com.xenapps.xenchat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
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
import com.google.firebase.firestore.ktx.toObject
import com.xenapps.xenchat.classes.User
import com.xenapps.xenchat.classes.UserAdapter
import com.xenapps.xenchat.utils.EncryptionUtils

class MainActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var nothingFoundLayout: RelativeLayout
    private lateinit var userAdapter: UserAdapter
    private val userList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initials()
        loadCurrentUser()
        setupUserListener()
    }

    private fun initials() {
        profileImageView = findViewById(R.id.profilePic)
        usernameTextView = findViewById(R.id.username)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        recyclerView = findViewById(R.id.recyclerView)
        nothingFoundLayout = findViewById(R.id.nothingFound)

        recyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter(userList) { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_UID", user.uid)
            startActivity(intent)
        }
        recyclerView.adapter = userAdapter
    }

    private fun loadCurrentUser() {
        val user = auth.currentUser
        user?.let {
            firestore.collection("users").document(it.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username")
                        val avatarUrl = document.getString("avatarUrl")
                        usernameTextView.text = username
                        if (avatarUrl != null) {
                            Glide.with(this)
                                .load(avatarUrl)
                                .transform(CircleCrop())
                                .into(profileImageView)
                        }
                    }
                }.addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to Load Image and Username: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupUserListener() {
        val currentUserUid = auth.currentUser?.uid ?: return
        firestore.collection("users").addSnapshotListener { snapshots, exception ->
            if (exception != null) {
                Toast.makeText(this, "Failed to Load Users: ${exception.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            userList.clear()
            for (document in snapshots!!.documents) {
                val user = document.toObject<User>()
                user?.uid = document.id
                if (user != null && user.uid != currentUserUid) {
                    loadLastMessage(user)
                }
            }
            userAdapter.notifyDataSetChanged()
            nothingFoundLayout.visibility = if (userList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun loadLastMessage(user: User) {
        val chatId = getChatId(auth.currentUser!!.uid, user.uid)
        firestore.collection("chats").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { messagesSnapshot, exception ->
                if (exception != null) {
                    Toast.makeText(this, "Failed to Load Last Message: ${exception.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (messagesSnapshot == null || messagesSnapshot.isEmpty) {
                    user.lastMessage = "No messages yet"
                } else {
                    val lastMessageDoc = messagesSnapshot.documents[0]
                    val encryptedMessage = lastMessageDoc.getString("message") ?: "No message"

                    // Decrypt the message
                    val decryptedMessage = try {
                        EncryptionUtils.decryptRSA(encryptedMessage, EncryptionUtils.getPrivateKey())
                    } catch (e: Exception) {
                        "Decryption error"
                    }

                    user.lastMessage = decryptedMessage
                }

                // Update the list
                val index = userList.indexOfFirst { it.uid == user.uid }
                if (index != -1) {
                    userList[index] = user
                } else {
                    userList.add(user)
                }
                userAdapter.notifyDataSetChanged()
                nothingFoundLayout.visibility = if (userList.isEmpty()) View.VISIBLE else View.GONE
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
