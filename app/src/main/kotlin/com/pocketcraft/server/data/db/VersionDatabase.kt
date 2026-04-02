package com.pocketcraft.server.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pocketcraft.server.data.model.MCVersion

@Database(
    entities = [MCVersion::class],
    version = 1,
    exportSchema = false
)
abstract class VersionDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionDao
}
