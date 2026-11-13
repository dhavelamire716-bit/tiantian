package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM generated_videos ORDER BY createdAtMillis DESC")
    fun observeAllVideos(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM generated_videos WHERE id = :id LIMIT 1")
    fun observeVideoById(id: Long): Flow<VideoEntity?>

    @Query("SELECT * FROM generated_videos WHERE id = :id LIMIT 1")
    suspend fun getVideoById(id: Long): VideoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Query("DELETE FROM generated_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)
}
