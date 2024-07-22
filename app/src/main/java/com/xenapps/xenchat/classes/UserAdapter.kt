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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.bind(user)
        holder.itemView.setOnClickListener { onUserClick(user) }
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chatProfilePic: ImageView = itemView.findViewById(R.id.chatProfilePic)
        private val chatUsername: TextView = itemView.findViewById(R.id.chatUsername)
        private val chatLastMessage: TextView = itemView.findViewById(R.id.chatLastMessage)
        private val unreadMark: View = itemView.findViewById(R.id.unreadMark) // Changed to View

        fun bind(user: User) {
            chatUsername.text = user.username
            chatLastMessage.text = user.lastMessage

            // Set unread message styling
            if (user.unread) {
                chatLastMessage.setTextColor(itemView.context.getColor(R.color.white)) // or any color for unread messages
                unreadMark.visibility = View.VISIBLE
            } else {
                chatLastMessage.setTextColor(itemView.context.getColor(R.color.white_60)) // or any color for read messages
                unreadMark.visibility = View.GONE
            }

            if (user.avatarUrl != null) {
                Glide.with(itemView.context)
                    .load(user.avatarUrl)
                    .transform(CircleCrop())
                    .into(chatProfilePic)
            }
        }
    }
}
