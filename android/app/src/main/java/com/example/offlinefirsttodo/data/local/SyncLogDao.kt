package com.example.offlinefirsttodo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY timestamp DESC, id DESC LIMIT 200")
    fun observeRecent(): Flow<List<SyncLogEntity>>

    @Query("SELECT * FROM sync_log WHERE kind != 'CONFLICT' ORDER BY timestamp DESC, id DESC LIMIT 1")
    fun observeLastRun(): Flow<SyncLogEntity?>

    @Query("DELETE FROM sync_log")
    suspend fun clear()
}
