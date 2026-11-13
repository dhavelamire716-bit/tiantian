package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "generated_videos")
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val prompt: String,
    val moodSummary: String,
    val aspectRatio: String,
    val style: String,
    val durationSeconds: Int,
    val sceneCount: Int,
    val videoFilePath: String,
    val thumbnailFilePath: String,
    val storyboardJson: String,
    val framePathsJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
