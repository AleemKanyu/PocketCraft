package com.pocketcraft.server.afk

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "afk_farm_locations")
data class AfkFarmLocationEntity(
    @PrimaryKey val id: String,
    val worldName: String,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val isActive: Boolean,
    val dummyEntityName: String,
    val dummyUuid: String = "",
    val ownerPlayerName: String = "",
    val ownerPlayerUuid: String = "",
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val createdAt: Long = 0L
)
