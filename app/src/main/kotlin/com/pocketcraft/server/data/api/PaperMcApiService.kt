package com.pocketcraft.server.data.api

import retrofit2.http.GET
import retrofit2.http.Path

interface PaperMcApiService {

    @GET("versions")
    suspend fun fetchAllVersions(): PaperVersionsResponse

    @GET("versions/{version}/builds")
    suspend fun fetchBuildsForVersion(@Path("version") version: String): PaperBuildsResponse
}

data class PaperVersionsResponse(
    val versions: List<String>
)

data class PaperBuildsResponse(
    val builds: List<PaperBuild>
)

data class PaperBuild(
    val build: Int,
    val channel: String,
    val downloads: BuildDownloads
)

data class BuildDownloads(
    val application: ApplicationInfo
)

data class ApplicationInfo(
    val name: String,
    val sha256: String,
    val size: Long
)
