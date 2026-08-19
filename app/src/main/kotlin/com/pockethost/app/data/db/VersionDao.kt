package com.pockethost.app.data.db

import androidx.room.*
import com.pockethost.app.data.model.MCVersion
import kotlinx.coroutines.flow.Flow

@Dao
interface VersionDao {

    @Query("SELECT * FROM mc_versions ORDER BY releaseTime DESC")
    fun getAllVersions(): Flow<List<MCVersion>>

    @Query("SELECT * FROM mc_versions WHERE id = :id LIMIT 1")
    suspend fun getVersionById(id: String): MCVersion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(versions: List<MCVersion>)

    @Query("DELETE FROM mc_versions")
    suspend fun deleteAll()
}
