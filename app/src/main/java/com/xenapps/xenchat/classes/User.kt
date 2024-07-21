package com.xenapps.xenchat.classes

data class User(
    var uid: String = "",
    val username: String = "",
    val avatarUrl: String? = null,
    var lastMessage: String = ""
)