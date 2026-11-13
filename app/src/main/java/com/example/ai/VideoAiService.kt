package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.CameraMotionStyle
import com.example.data.StoryboardPlan
import com.example.data.VideoAspectRatio
import com.example.data.VideoStyle
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json

private const val TAG = "VideoAiService"

class VideoAiService {

    private val json = Json { ignoreUnknownKeys = true }

    private val promptEnhancerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.85f
            }
        )
    }

    private val storyboardSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short evocative 2-5 word title for the video reel"),
            "moodSummary" to Schema.string(description = "One sentence summarizing the color mood and atmosphere"),
            "scenes" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "sceneNumber" to Schema.integer(description = "1-based index of the scene"),
                        "visualPrompt" to Schema.string(
                            description = "Rich standalone visual image description for this shot including subject, lighting, composition, and environment"
                        ),
                        "caption" to Schema.string(
                            description = "Short poetic 4-8 word cinematic subtitle caption for this shot"
                        ),
                        "cameraMotion" to Schema.enumeration(
                            values = listOf("ZOOM_IN", "ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT", "DRIFT_UP"),
                            description = "Camera movement direction for this shot"
                        ),
                        "durationSeconds" to Schema.integer(
                            description = "Shot duration in seconds, between 3 and 4"
                        )
                    )
                ),
                description = "Ordered list of visual scenes that tell a cohesive mini video"
            )
        )
    )

    private val storyboardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = storyboardSchema
                temperature = 0.7f
            }
        )
    }

    private fun imageModelFor(aspectRatio: VideoAspectRatio) =
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
                imageConfig = ImageConfig.builder()
                    .setAspectRatio(aspectRatio.apiAspectRatio)
                    .setImageSize(ImageSize.SIZE_1K)
                    .build()
            }
        )

    suspend fun enhancePrompt(rawPrompt: String, style: VideoStyle): String {
        val instruction = """
            Rewrite the user's video idea into one vivid, cinematic 2-3 sentence video prompt tailored for a ${style.label} aesthetic.
            Focus on lighting, camera framing, motion, atmosphere, and rich visual texture.
            Return ONLY the enhanced prompt text with no quotes or markdown.

            User idea: $rawPrompt
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                promptEnhancerModel.generateContent(instruction)
            }
            response.text?.trim()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI returned an empty prompt enhancement.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Prompt enhancement timed out", e)
            throw IllegalStateException("Prompt enhancement timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prompt enhancement failed", e)
            throw e
        }
    }

    suspend fun generateStoryboard(
        prompt: String,
        style: VideoStyle,
        sceneCount: Int,
        cameraStyle: CameraMotionStyle
    ): StoryboardPlan {
        val cameraDirective = when (cameraStyle) {
            CameraMotionStyle.AUTO -> "Vary cameraMotion naturally across shots."
            CameraMotionStyle.SMOOTH_ZOOM -> "Prefer ZOOM_IN and ZOOM_OUT camera motions."
            CameraMotionStyle.CINEMA_PAN -> "Prefer PAN_LEFT and PAN_RIGHT camera motions."
            CameraMotionStyle.PARALLAX_DRIFT -> "Prefer DRIFT_UP and ZOOM_IN camera motions."
        }

        val directorPrompt = """
            You are an acclaimed film director creating a $sceneCount-scene visual storyboard for a short AI video reel.
            User Prompt: "$prompt"
            Visual Style: ${style.label} (${style.promptDirective})
            Camera Direction: $cameraDirective

            Rules:
            - Create exactly $sceneCount sequential scenes that evolve smoothly from opening shot to finale.
            - Each scene's visualPrompt must be self-contained and detailed (no text or watermarks in the image).
            - Each scene's caption must be 4-8 evocative words.
            - Set durationSeconds to 3 for each scene.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                storyboardModel.generateContent(directorPrompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Storyboard model returned an empty response.")
            val parsed = json.decodeFromString<StoryboardPlan>(jsonText)
            if (parsed.scenes.isEmpty()) {
                throw IllegalStateException("Storyboard contained no scenes.")
            }
            parsed
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Storyboard generation timed out", e)
            throw IllegalStateException("Storyboard generation timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Storyboard generation failed", e)
            throw e
        }
    }

    suspend fun generateSceneFrame(
        scenePrompt: String,
        style: VideoStyle,
        aspectRatio: VideoAspectRatio
    ): Bitmap {
        val fullPrompt = "$scenePrompt, ${style.promptDirective}, ultra detailed single film frame, no text, no watermark, no borders"
        return try {
            val model = imageModelFor(aspectRatio)
            val response = withTimeout(60_000L) {
                model.generateContent(fullPrompt)
            }
            val imagePart = response.candidates.firstOrNull()
                ?.content?.parts
                ?.filterIsInstance<com.google.firebase.ai.type.ImagePart>()
                ?.firstOrNull()

            imagePart?.image
                ?: throw IllegalStateException("No image frame was returned by Gemini Nano Banana.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Scene frame generation timed out", e)
            throw IllegalStateException("Scene visual generation timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Scene frame generation failed", e)
            throw e
        }
    }
}
