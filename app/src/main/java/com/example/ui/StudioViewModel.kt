package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.VideoAiService
import com.example.data.CameraMotionStyle
import com.example.data.VideoAspectRatio
import com.example.data.VideoLibraryRepository
import com.example.data.VideoStyle
import com.example.data.local.VerdeReelDatabase
import com.example.data.local.VideoEntity
import com.example.video.Mp4ReelRenderer
import com.example.video.SceneRenderInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

private const val TAG = "StudioViewModel"

enum class GenerationStage {
    STORYBOARD,
    KEYFRAMES,
    ENCODING
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data object EnhancingPrompt : GenerationUiState
    data class Generating(
        val stage: GenerationStage,
        val progress: Float,
        val statusDetail: String,
        val planTitle: String? = null,
        val totalScenes: Int = 0,
        val previewFramePaths: List<String> = emptyList()
    ) : GenerationUiState

    data class Completed(
        val videoId: Long,
        val title: String
    ) : GenerationUiState

    data class Error(val message: String) : GenerationUiState
}

data class StudioControlsState(
    val prompt: String = "",
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.LANDSCAPE_16_9,
    val style: VideoStyle = VideoStyle.CINEMATIC,
    val cameraMotion: CameraMotionStyle = CameraMotionStyle.AUTO,
    val sceneCount: Int = 4,
    val burnInCaptions: Boolean = true,
    val isDarkTheme: Boolean = true
)

class StudioViewModel(
    private val app: Application,
    private val repository: VideoLibraryRepository,
    private val aiService: VideoAiService,
    private val renderer: Mp4ReelRenderer
) : AndroidViewModel(app) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _controls = MutableStateFlow(StudioControlsState())
    val controls: StateFlow<StudioControlsState> = _controls.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _exportBanner = MutableStateFlow<String?>(null)
    val exportBanner: StateFlow<String?> = _exportBanner.asStateFlow()

    val videos: StateFlow<List<VideoEntity>> = repository.allVideos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private var activeJob: Job? = null

    fun updatePrompt(newPrompt: String) {
        _controls.update { it.copy(prompt = newPrompt) }
    }

    fun updateAspectRatio(aspectRatio: VideoAspectRatio) {
        _controls.update { it.copy(aspectRatio = aspectRatio) }
    }

    fun updateStyle(style: VideoStyle) {
        _controls.update { it.copy(style = style) }
    }

    fun updateCameraMotion(cameraMotion: CameraMotionStyle) {
        _controls.update { it.copy(cameraMotion = cameraMotion) }
    }

    fun updateSceneCount(count: Int) {
        _controls.update { it.copy(sceneCount = count.coerceIn(3, 5)) }
    }

    fun updateBurnInCaptions(enabled: Boolean) {
        _controls.update { it.copy(burnInCaptions = enabled) }
    }

    fun toggleTheme() {
        _controls.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun clearBanner() {
        _exportBanner.value = null
    }

    fun resetGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun loadPromptFromVideo(video: VideoEntity) {
        val ratio = VideoAspectRatio.entries.firstOrNull { it.label == video.aspectRatio }
            ?: VideoAspectRatio.LANDSCAPE_16_9
        val style = VideoStyle.entries.firstOrNull { it.label == video.style }
            ?: VideoStyle.CINEMATIC
        _controls.update {
            it.copy(
                prompt = video.prompt,
                aspectRatio = ratio,
                style = style,
                sceneCount = video.sceneCount.coerceIn(3, 5)
            )
        }
    }

    fun enhancePrompt() {
        val currentPrompt = _controls.value.prompt.trim()
        if (currentPrompt.isBlank()) {
            _generationState.value = GenerationUiState.Error("Write a short video idea first so AI can enhance it.")
            return
        }
        if (activeJob?.isActive == true) return

        activeJob = viewModelScope.launch {
            _generationState.value = GenerationUiState.EnhancingPrompt
            try {
                val enhanced = aiService.enhancePrompt(currentPrompt, _controls.value.style)
                _controls.update { it.copy(prompt = enhanced) }
                _generationState.value = GenerationUiState.Idle
            } catch (e: CancellationException) {
                _generationState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Prompt enhancement failed", e)
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Failed to enhance prompt."
                )
            }
        }
    }

    fun generateVideo() {
        val snapshot = _controls.value
        val prompt = snapshot.prompt.trim()
        if (prompt.isBlank()) {
            _generationState.value = GenerationUiState.Error("Enter a video prompt to start generating.")
            return
        }
        if (activeJob?.isActive == true) return

        activeJob = viewModelScope.launch {
            val timestamp = System.currentTimeMillis()
            val framesDir = File(app.filesDir, "frames/reel_$timestamp").apply { mkdirs() }
            val videosDir = File(app.filesDir, "videos").apply { mkdirs() }
            val outputVideoFile = File(videosDir, "verdereel_$timestamp.mp4")

            val sceneBitmaps = mutableListOf<Bitmap>()
            val framePaths = mutableListOf<String>()

            try {
                // Stage 1: AI Director Storyboard
                _generationState.value = GenerationUiState.Generating(
                    stage = GenerationStage.STORYBOARD,
                    progress = 0.08f,
                    statusDetail = "Directing ${snapshot.sceneCount}-scene storyboard with Gemini...",
                    totalScenes = snapshot.sceneCount
                )

                val plan = aiService.generateStoryboard(
                    prompt = prompt,
                    style = snapshot.style,
                    sceneCount = snapshot.sceneCount,
                    cameraStyle = snapshot.cameraMotion
                )

                val scenes = plan.scenes.take(snapshot.sceneCount)
                val totalScenes = scenes.size

                // Stage 2: Keyframe Visual Synthesis
                scenes.forEachIndexed { index, scene ->
                    _generationState.value = GenerationUiState.Generating(
                        stage = GenerationStage.KEYFRAMES,
                        progress = 0.15f + (0.60f * (index.toFloat() / totalScenes.toFloat())),
                        statusDetail = "Synthesizing scene ${index + 1} of $totalScenes...",
                        planTitle = plan.title,
                        totalScenes = totalScenes,
                        previewFramePaths = framePaths.toList()
                    )

                    val bitmap = aiService.generateSceneFrame(
                        scenePrompt = scene.visualPrompt,
                        style = snapshot.style,
                        aspectRatio = snapshot.aspectRatio
                    )
                    sceneBitmaps.add(bitmap)

                    val frameFile = File(framesDir, "scene_${index + 1}.jpg")
                    saveBitmapJpeg(bitmap, frameFile)
                    framePaths.add(frameFile.absolutePath)

                    _generationState.value = GenerationUiState.Generating(
                        stage = GenerationStage.KEYFRAMES,
                        progress = 0.15f + (0.60f * ((index + 1).toFloat() / totalScenes.toFloat())),
                        statusDetail = "Captured scene ${index + 1} of $totalScenes",
                        planTitle = plan.title,
                        totalScenes = totalScenes,
                        previewFramePaths = framePaths.toList()
                    )
                }

                // Stage 3: On-device MP4 Rendering via MediaCodec + OpenGL ES
                _generationState.value = GenerationUiState.Generating(
                    stage = GenerationStage.ENCODING,
                    progress = 0.78f,
                    statusDetail = "Encoding H.264 MP4 motion reel...",
                    planTitle = plan.title,
                    totalScenes = totalScenes,
                    previewFramePaths = framePaths.toList()
                )

                val renderInputs = scenes.zip(sceneBitmaps) { scene, bmp ->
                    SceneRenderInput(scene = scene, bitmap = bmp)
                }

                renderer.renderMp4(
                    scenes = renderInputs,
                    outputFile = outputVideoFile,
                    width = snapshot.aspectRatio.width,
                    height = snapshot.aspectRatio.height,
                    burnInCaptions = snapshot.burnInCaptions,
                    onProgress = { encodeFraction ->
                        _generationState.value = GenerationUiState.Generating(
                            stage = GenerationStage.ENCODING,
                            progress = 0.78f + (0.20f * encodeFraction),
                            statusDetail = "Encoding video ${(encodeFraction * 100).toInt()}%...",
                            planTitle = plan.title,
                            totalScenes = totalScenes,
                            previewFramePaths = framePaths.toList()
                        )
                    }
                )

                val totalDuration = scenes.sumOf { it.durationSeconds.coerceIn(2, 5) }
                val entity = VideoEntity(
                    title = plan.title.ifBlank { "Emerald Reel" },
                    prompt = prompt,
                    moodSummary = plan.moodSummary,
                    aspectRatio = snapshot.aspectRatio.label,
                    style = snapshot.style.label,
                    durationSeconds = totalDuration,
                    sceneCount = totalScenes,
                    videoFilePath = outputVideoFile.absolutePath,
                    thumbnailFilePath = framePaths.first(),
                    storyboardJson = json.encodeToString(plan),
                    framePathsJson = json.encodeToString(framePaths),
                    createdAtMillis = timestamp
                )
                val insertedId = repository.saveGeneratedVideo(entity)

                _generationState.value = GenerationUiState.Completed(
                    videoId = insertedId,
                    title = entity.title
                )
            } catch (e: CancellationException) {
                runCatching { outputVideoFile.delete() }
                runCatching { framesDir.deleteRecursively() }
                _generationState.value = GenerationUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video generation pipeline failed", e)
                runCatching { outputVideoFile.delete() }
                runCatching { framesDir.deleteRecursively() }
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Video generation failed. Check network or quota and retry."
                )
            } finally {
                sceneBitmaps.forEach { runCatching { if (!it.isRecycled) it.recycle() } }
            }
        }
    }

    fun cancelGeneration() {
        activeJob?.cancel()
        activeJob = null
        _generationState.value = GenerationUiState.Idle
    }

    fun deleteVideo(video: VideoEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            _exportBanner.value = "Deleted \"${video.title}\""
        }
    }

    fun exportVideoToGallery(video: VideoEntity) {
        viewModelScope.launch {
            val result = repository.exportToGallery(video)
            _exportBanner.value = result.fold(
                onSuccess = { name -> "Saved $name to Movies/VerdeReel" },
                onFailure = { err -> err.localizedMessage ?: "Failed to save video to Gallery." }
            )
        }
    }

    fun observeVideo(videoId: Long) = repository.observeVideo(videoId)

    fun getShareUri(videoFilePath: String) = repository.getShareUri(videoFilePath)

    private suspend fun saveBitmapJpeg(bitmap: Bitmap, file: File) = withContext(Dispatchers.IO) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val db = VerdeReelDatabase.getInstance(application)
                val repo = VideoLibraryRepository(application, db.videoDao())
                StudioViewModel(
                    app = application,
                    repository = repo,
                    aiService = VideoAiService(),
                    renderer = Mp4ReelRenderer()
                )
            }
        }
    }
}
