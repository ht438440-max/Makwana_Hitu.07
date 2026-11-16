package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY uploadedAtMs DESC")
    fun observeAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<VideoEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoEntity): Long

    @Query("UPDATE videos SET title = :newTitle WHERE id = :id")
    suspend fun rename(id: Long, newTitle: String)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM videos")
    fun observeTotalUsedBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM videos")
    fun observeVideoCount(): Flow<Int>
}

@Dao
interface RtmpConfigDao {
    @Query("SELECT * FROM rtmp_configs ORDER BY lastUsedAtMs DESC")
    fun observeAllConfigs(): Flow<List<RtmpConfigEntity>>

    @Query("SELECT * FROM rtmp_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RtmpConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: RtmpConfigEntity): Long

    @Query("UPDATE rtmp_configs SET lastUsedAtMs = :timestampMs WHERE id = :id")
    suspend fun touchLastUsed(id: Long, timestampMs: Long = System.currentTimeMillis())

    @Query("DELETE FROM rtmp_configs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface StreamHistoryDao {
    @Query("SELECT * FROM stream_history ORDER BY startedAtMs DESC")
    fun observeAllHistory(): Flow<List<StreamHistoryEntity>>

    @Query("SELECT COUNT(*) FROM stream_history")
    fun observeHistoryCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: StreamHistoryEntity): Long

    @Query("UPDATE stream_history SET durationMs = :durationMs, status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateSession(id: Long, durationMs: Long, status: String, errorMessage: String?)

    @Query("DELETE FROM stream_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM stream_history")
    suspend fun clearAll()
}
