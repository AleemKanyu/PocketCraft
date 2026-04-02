package com.pocketcraft.server.data.api

import com.pocketcraft.server.data.model.MCVersion
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

// Version manifest response wrapper
data class VersionManifestResponse(
    val latest: LatestVersions,
    val versions: List<MCVersionResponse>
)

data class LatestVersions(
    val release: String,
    val snapshot: String
)

data class MCVersionResponse(
    val id: String,
    val type: String,
    val url: String,
    @SerializedName("releaseTime") val releaseTime: String
) {
    fun toMCVersion() = MCVersion(id = id, type = type, url = url, releaseTime = releaseTime)
}

interface MojangApiService {
    @GET("mc/game/version_manifest.json")
    suspend fun getVersionManifest(): VersionManifestResponse
}
