package com.xenapps.xenchat.classes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.xenapps.xenchat.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private var messages: List<Any>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Define view types
    private val VIEW_TYPE_DATE_HEADER = 0
    private val VIEW_TYPE_MESSAGE_SENT = 1
    private val VIEW_TYPE_MESSAGE_RECEIVED = 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            VIEW_TYPE_MESSAGE_SENT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_sent, parent, false)
                MessageSentViewHolder(view)
            }
            VIEW_TYPE_MESSAGE_RECEIVED -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_received, parent, false)
                MessageReceivedViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            VIEW_TYPE_DATE_HEADER -> {
                val dateHeader = messages[position] as String
                (holder as DateHeaderViewHolder).bind(dateHeader)
            }
            VIEW_TYPE_MESSAGE_SENT -> {
                val message = messages[position] as Message
                (holder as MessageSentViewHolder).bind(message)
            }
            VIEW_TYPE_MESSAGE_RECEIVED -> {
                val message = messages[position] as Message
                (holder as MessageReceivedViewHolder).bind(message)
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemViewType(position: Int): Int {
        return when (messages[position]) {
            is String -> VIEW_TYPE_DATE_HEADER
            is Message -> {
                val message = messages[position] as Message
                if (message.senderId == FirebaseAuth.getInstance().currentUser?.uid) {
                    VIEW_TYPE_MESSAGE_SENT
                } else {
                    VIEW_TYPE_MESSAGE_RECEIVED
                }
            }
            else -> throw IllegalArgumentException("Invalid item type")
        }
    }

    fun updateMessages(newMessages: List<Any>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    companion object {
        private fun formatTimestamp(timestamp: Long): String {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    // ViewHolder classes
    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateHeaderTextView: TextView = view.findViewById(R.id.dateHeaderTextView)

        fun bind(dateHeader: String) {
            dateHeaderTextView.text = dateHeader
        }
    }

    class MessageSentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageTextView: TextView = view.findViewById(R.id.messageText)
        private val messageTimeTextView: TextView = view.findViewById(R.id.messageTime)

        fun bind(message: Message) {
            messageTextView.text = message.message
            messageTimeTextView.text = formatTimestamp(message.timestamp)

            // Handle delivery and seen status if needed
            if (message.isDelivered) {
                // Show delivered status
            }

            if (message.isSeen) {
                // Show seen status
            }
        }
    }

    class MessageReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageTextView: TextView = view.findViewById(R.id.messageText)
        private val messageTimeTextView: TextView = view.findViewById(R.id.messageTime)

        fun bind(message: Message) {
            messageTextView.text = message.message
            messageTimeTextView.text = formatTimestamp(message.timestamp)

            // Handle delivery and seen status if needed
            if (message.isDelivered) {
                // Show delivered status
            }

            if (message.isSeen) {
                // Show seen status
            }
        }
    }
}







