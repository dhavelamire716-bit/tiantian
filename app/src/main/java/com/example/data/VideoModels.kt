package com.example.data

import com.google.firebase.ai.type.AspectRatio
import kotlinx.serialization.Serializable

enum class VideoAspectRatio(
    val label: String,
    val width: Int,
    val height: Int,
    val apiAspectRatio: AspectRatio
) {
    LANDSCAPE_16_9("16:9", 1280, 720, AspectRatio.LANDSCAPE_16x9),
    PORTRAIT_9_16("9:16", 720, 1280, AspectRatio.PORTRAIT_9x16),
    SQUARE_1_1("1:1", 720, 720, AspectRatio.SQUARE_1x1)
}

enum class VideoStyle(
    val label: String,
    val promptDirective: String
) {
    CINEMATIC(
        "Cinematic",
        "cinematic 35mm film photography, anamorphic lens, rich emerald shadows, volumetric lighting, photorealistic"
    ),
    BOTANICAL(
        "Botanical",
        "lush National Geographic nature cinematography, macro emerald foliage, morning mist, golden-green sunlight"
    ),
    CYBER_EMERALD(
        "Cyber Neon",
        "futuristic emerald neon cyberpunk cinematography, wet reflective streets, jade holograms, moody atmospheric depth"
    ),
    ANIME(
        "Anime",
        "high-budget Studio Ghibli and Makoto Shinkai inspired anime film frame, lush verdant scenery, glowing atmospheric light"
    ),
    WATERCOLOR(
        "Watercolor",
        "expressive hand-painted emerald and teal watercolor cinema illustration, textured paper grain, luminous washes"
    ),
    NOIR_JADE(
        "Noir Jade",
        "stylized dark emerald noir cinematography, dramatic chiaroscuro rim lighting, deep pine shadows, crisp silhouettes"
    )
}

enum class CameraMotionStyle(val label: String) {
    AUTO("Auto Director"),
    SMOOTH_ZOOM("Smooth Zoom"),
    CINEMA_PAN("Cinema Pan"),
    PARALLAX_DRIFT("Parallax Drift")
}

enum class SceneCameraMotion {
    ZOOM_IN,
    ZOOM_OUT,
    PAN_LEFT,
    PAN_RIGHT,
    DRIFT_UP;

    companion object {
        fun fromString(raw: String): SceneCameraMotion {
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: ZOOM_IN
        }
    }
}

@Serializable
data class StoryboardScene(
    val sceneNumber: Int,
    val visualPrompt: String,
    val caption: String,
    val cameraMotion: String,
    val durationSeconds: Int = 3
)

@Serializable
data class StoryboardPlan(
    val title: String,
    val moodSummary: String,
    val scenes: List<StoryboardScene>
)

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Studio : AppRoute

    @Serializable
    data object Library : AppRoute

    @Serializable
    data class Player(val videoId: Long) : AppRoute
}

val SampleVideoPrompts = listOf(
    "A bioluminescent emerald rainforest waterfall at twilight, fireflies drifting through misty ferns, slow cinematic drone reveal",
    "Rain-soaked futuristic Kyoto alley glowing with jade and lime neon signs, reflections on cobblestones, steam rising",
    "An ancient glass conservatory filled with rare glowing orchids waking up under golden-green sunrise rays",
    "A sleek electric hypercar gliding silently along a misty mountain pass lined with towering pine trees",
    "Tiny glowing moss spirits tending a miniature floating bonsai island in a tranquil jade sky"
)
