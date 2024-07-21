package com.xenapps.xenchat.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.xenapps.xenchat.R

class UserAdapter(
    private val userList: List<User>,
    private val onUserClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profilePic: ImageView = itemView.findViewById(R.id.chatProfilePic)
        val username: TextView = itemView.findViewById(R.id.chatUsername)
        val lastMessage: TextView = itemView.findViewById(R.id.chatLastMessage) // Optional, if you plan to use it
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]

        holder.username.text = user.username
        holder.lastMessage.text = user.lastMessage

        if (user.avatarUrl?.isNotEmpty() == true) {
            Glide.with(holder.itemView.context)
                .load(user.avatarUrl)
                .transform(CircleCrop())
                .into(holder.profilePic)
        }

        holder.itemView.setOnClickListener {
            onUserClick(user)
        }
    }

    override fun getItemCount() = userList.size
}