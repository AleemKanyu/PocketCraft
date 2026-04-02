package com.pocketcraft.server.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single Minecraft Java Edition release.
 * Stored in Room for offline access.
 */
@Entity(tableName = "mc_versions")
data class MCVersion(
    @PrimaryKey val id: String,
    val type: String,        // "release" or "snapshot"
    val url: String,         // Points to per-version detail JSON
    val releaseTime: String
)
