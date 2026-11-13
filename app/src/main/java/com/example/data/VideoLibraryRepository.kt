package com.example.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.VideoDao
import com.example.data.local.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream

class VideoLibraryRepository(
    private val context: Context,
    private val videoDao: VideoDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    val allVideos: Flow<List<VideoEntity>> = videoDao.observeAllVideos()

    fun observeVideo(id: Long): Flow<VideoEntity?> = videoDao.observeVideoById(id)

    suspend fun getVideo(id: Long): VideoEntity? = videoDao.getVideoById(id)

    suspend fun saveGeneratedVideo(video: VideoEntity): Long = videoDao.insertVideo(video)

    suspend fun deleteVideo(video: VideoEntity) {
        withContext(Dispatchers.IO) {
            runCatching { File(video.videoFilePath).delete() }
            runCatching { File(video.thumbnailFilePath).delete() }
            runCatching {
                val frames = json.decodeFromString<List<String>>(video.framePathsJson)
                frames.forEach { path -> File(path).delete() }
            }
        }
        videoDao.deleteVideoById(video.id)
    }

    fun getShareUri(videoFilePath: String): Uri? {
        val file = File(videoFilePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    suspend fun exportToGallery(video: VideoEntity): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(video.videoFilePath)
            require(sourceFile.exists()) { "Generated video file could not be found." }

            val safeName = video.title
                .replace(Regex("[^A-Za-z0-9_\\- ]"), "")
                .trim()
                .replace(' ', '_')
                .ifBlank { "VerdeReel_${video.id}" }
            val displayName = "${safeName}_${System.currentTimeMillis()}.mp4"

            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/VerdeReel"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Could not create MediaStore entry for video.")

            try {
                resolver.openOutputStream(itemUri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IllegalStateException("Could not open MediaStore output stream.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }
                displayName
            } catch (e: Exception) {
                resolver.delete(itemUri, null, null)
                throw e
            }
        }
    }
}
