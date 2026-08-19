package com.pockethost.app.afk

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AfkFarmLocationDao {
    @Query("SELECT * FROM afk_farm_locations ORDER BY createdAt ASC, name ASC")
    fun observeAll(): Flow<List<AfkFarmLocationEntity>>

    @Query("SELECT * FROM afk_farm_locations ORDER BY createdAt ASC, name ASC")
    suspend fun getAll(): List<AfkFarmLocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(farm: AfkFarmLocationEntity)

    @Delete
    suspend fun delete(farm: AfkFarmLocationEntity)
}
