package com.pocketcraft.server.data.model

data class VersionDetail(
    val id: String,
    val downloads: Downloads
)

data class Downloads(
    val server: ServerDownload
)

data class ServerDownload(
    val url: String,
    val size: Long
)
