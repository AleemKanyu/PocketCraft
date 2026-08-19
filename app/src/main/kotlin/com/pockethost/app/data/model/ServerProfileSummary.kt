package com.pockethost.app.data.model

data class ServerProfileSummary(
    val name: String,
    val worldName: String,
    val port: Int,
    val motd: String,
    val isActive: Boolean
)
