package com.xenapps.xenchat.classes

data class User(
    var uid: String = "",
    var username: String = "",
    var avatarUrl: String? = null,
    var lastMessage: String = "",
    var unread: Boolean = false // Add this field
)
