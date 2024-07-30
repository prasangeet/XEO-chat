package com.xenapps.xenchat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.xenapps.xenchat.classes.User
import com.xenapps.xenchat.classes.UserAdapter
import com.xenapps.xenchat.utils.EncryptionUtils
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var profileImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var nothingFoundLayout: RelativeLayout
    private lateinit var userAdapter: UserAdapter
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tabLayout: TabLayout
    private val userList = mutableListOf<User>()
    private val filteredUserList = mutableListOf<User>()
    private val favoriteUserIds = mutableListOf<String>()
    private var selectedTabPosition: Int = 0
    private lateinit var profileLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        loadCurrentUser()
        loadProfile()
        setupUserListener()
        setupTabLayout()
        setupSearchView()
        setupSwipeRefresh()
        loadFavoriteUserIds()
    }

    private fun initializeViews() {
        profileImageView = findViewById(R.id.profilePic)
        usernameTextView = findViewById(R.id.username)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        recyclerView = findViewById(R.id.recyclerView)
        nothingFoundLayout = findViewById(R.id.nothingFound)
        searchEditText = findViewById(R.id.searchUsers)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        tabLayout = findViewById(R.id.tabLayout)
        profileLayout = findViewById(R.id.profileLayout)

        recyclerView.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdapter(filteredUserList, { user ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("USER_UID", user.uid)
            startActivity(intent)
        }, { user ->
            toggleFavorite(user)
        }, favoriteUserIds)
        recyclerView.adapter = userAdapter
    }

    private fun loadProfile(){
        profileLayout.setOnClickListener{
            startActivity(Intent(this@MainActivity, ProfileActivity::class.java))
        }
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
                        avatarUrl?.let { url ->
                            Glide.with(this)
                                .load(url)
                                .transform(CircleCrop())
                                .into(profileImageView)
                        }
                    } else {
                        Log.d("MainActivity", "Current user document does not exist.")
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
                swipeRefreshLayout.isRefreshing = false
                return@addSnapshotListener
            }

            userList.clear()
            Log.d("MainActivity", "Snapshot received with ${snapshots?.documents?.size ?: 0} users.")
            snapshots?.documents?.forEach { document ->
                val user = document.toObject<User>()?.apply { uid = document.id }
                Log.d("MainActivity", "Processing user: ${user?.username}, uid: ${user?.uid}")
                user?.takeIf { it.uid != currentUserUid }?.let { loadedUser ->
                    loadLastMessage(loadedUser)
                }
            }
            filterUsers(getCurrentFilterQuery())
        }
    }

    private fun loadFavoriteUserIds() {
        val currentUserUid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(currentUserUid).collection("favorites")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("MainActivity", "Failed to load favorites: ${e.message}")
                    return@addSnapshotListener
                }
                favoriteUserIds.clear()
                favoriteUserIds.addAll(snapshot?.documents?.map { it.id } ?: emptyList())
                filterUsers(getCurrentFilterQuery())
            }
    }

    private fun loadLastMessage(user: User) {
        val currentUserUid = auth.currentUser!!.uid
        val chatId = getChatId(currentUserUid, user.uid)
        val messagesRef = firestore.collection("chats").document(chatId).collection("messages")

        messagesRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { messagesSnapshot, exception ->
                if (exception != null) {
                    Log.e("MainActivity", "Failed to load last message: ${exception.message}")
                    user.lastMessage = "Error fetching message"
                    user.unread = false
                } else if (messagesSnapshot == null || messagesSnapshot.isEmpty) {
                    user.lastMessage = "No messages yet"
                    user.unread = false
                    Log.d("MainActivity", "No messages found for user: ${user.uid}")
                } else {
                    val lastMessageDoc = messagesSnapshot.documents[0]
                    val encryptedMessage = lastMessageDoc.getString("message") ?: "No message"
                    val isSeen = lastMessageDoc.getBoolean("seen") ?: true
                    val senderId = lastMessageDoc.getString("senderId") ?: currentUserUid

                    Log.d("MainActivity", "Retrieved message: $encryptedMessage")

                    user.lastMessage = try {
                        EncryptionUtils.decryptRSA(encryptedMessage, EncryptionUtils.getPrivateKey())
                    } catch (e: Exception) {
                        "Decryption error"
                    }

                    // Format the last message string
                    user.lastMessage = if (senderId == currentUserUid) {
                        "You: ${user.lastMessage}"
                    } else {
                        "${user.username}: ${user.lastMessage}"
                    }

                    user.unread = (senderId != currentUserUid) && !isSeen
                    Log.d("MainActivity", "Decrypted message: ${user.lastMessage}")
                }

                val index = userList.indexOfFirst { it.uid == user.uid }
                if (index != -1) {
                    userList[index] = user
                } else {
                    userList.add(user)
                }
                filterUsers(getCurrentFilterQuery())
            }
    }

    private fun getChatId(currentUserId: String, otherUserId: String): String {
        return if (currentUserId > otherUserId) {
            "$currentUserId-$otherUserId"
        } else {
            "$otherUserId-$currentUserId"
        }
    }

    private fun filterUsers(query: String) {
        filteredUserList.clear()
        if (query.isEmpty() || query == "all") {
            filteredUserList.addAll(userList)
        } else if (query == "favorites") {
            filteredUserList.addAll(userList.filter { favoriteUserIds.contains(it.uid) })
        } else {
            val lowerCaseQuery = query.toLowerCase(Locale.getDefault())
            filteredUserList.addAll(userList.filter { it.username.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery) })
        }
        userAdapter.notifyDataSetChanged()
        nothingFoundLayout.visibility = if (filteredUserList.isEmpty()) View.VISIBLE else View.GONE
        swipeRefreshLayout.isRefreshing = false
    }

    private fun toggleFavorite(user: User) {
        val currentUserUid = auth.currentUser?.uid ?: return
        val userRef = firestore.collection("users").document(currentUserUid).collection("favorites").document(user.uid)

        userRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                // If the user is already in favorites, remove them
                userRef.delete()
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Removed ${user.username} from favorites.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Error removing favorite: ${e.message}")
                    }
            } else {
                // Add the user to favorites
                userRef.set(mapOf("timestamp" to System.currentTimeMillis()))
                    .addOnSuccessListener {
                        Log.d("MainActivity", "Added ${user.username} to favorites.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "Error adding favorite: ${e.message}")
                    }
            }
        }
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTabPosition = tab.position
                filterUsers(getCurrentFilterQuery())
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun getCurrentFilterQuery(): String {
        return when (selectedTabPosition) {
            0 -> searchEditText.text.toString()
            1 -> "favorites"
            else -> ""
        }
    }

    private fun setupSearchView() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                filterUsers(getCurrentFilterQuery())
            }

            override fun afterTextChanged(editable: Editable) {}
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadCurrentUser()
            loadFavoriteUserIds()
        }
    }
}
