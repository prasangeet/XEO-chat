package com.xenapps.xenchat.classes

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isDelivered: Boolean = false,
    val isSeen: Boolean = false
)
