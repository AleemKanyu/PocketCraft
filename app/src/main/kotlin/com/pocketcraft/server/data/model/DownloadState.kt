package com.pocketcraft.server.data.model

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Done : DownloadState()
    data class Error(val message: String) : DownloadState()
}
