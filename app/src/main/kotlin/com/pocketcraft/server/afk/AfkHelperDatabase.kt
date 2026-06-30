package com.pocketcraft.server.afk

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AfkFarmLocationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AfkHelperDatabase : RoomDatabase() {
    abstract fun afkFarmLocationDao(): AfkFarmLocationDao

    companion object {
        @Volatile
        private var instance: AfkHelperDatabase? = null

        fun getInstance(context: Context): AfkHelperDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AfkHelperDatabase::class.java,
                    "pocketcraft_afk_helpers.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
