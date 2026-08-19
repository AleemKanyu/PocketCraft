package com.pockethost.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pockethost.app.data.model.MCVersion

@Database(
    entities = [MCVersion::class],
    version = 1,
    exportSchema = false
)
abstract class VersionDatabase : RoomDatabase() {
    abstract fun versionDao(): VersionDao
}
