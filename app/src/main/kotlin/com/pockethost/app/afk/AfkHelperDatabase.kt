package com.pockethost.app.afk

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
        @Volatile private var INSTANCE: AfkHelperDatabase? = null

        fun getInstance(context: Context): AfkHelperDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AfkHelperDatabase::class.java,
                    "pocketcraft_afk_helpers.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
