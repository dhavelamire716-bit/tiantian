package com.example

import com.example.data.SceneCameraMotion
import com.example.data.StoryboardPlan
import com.example.data.VideoAspectRatio
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun storyboardPlan_deserializesValidJson() {
        val raw = """
            {
              "title": "Emerald Mist",
              "moodSummary": "Luminous jade twilight in an ancient forest.",
              "scenes": [
                {
                  "sceneNumber": 1,
                  "visualPrompt": "Mossy waterfall glowing at twilight",
                  "caption": "Twilight awakens the emerald canopy",
                  "cameraMotion": "ZOOM_IN",
                  "durationSeconds": 3
                },
                {
                  "sceneNumber": 2,
                  "visualPrompt": "Fireflies rising over fern fronds",
                  "caption": "Lanterns drift through silent ferns",
                  "cameraMotion": "PAN_RIGHT",
                  "durationSeconds": 3
                }
              ]
            }
        """.trimIndent()

        val plan = json.decodeFromString<StoryboardPlan>(raw)
        assertEquals("Emerald Mist", plan.title)
        assertEquals(2, plan.scenes.size)
        assertEquals(SceneCameraMotion.ZOOM_IN, SceneCameraMotion.fromString(plan.scenes[0].cameraMotion))
        assertEquals(SceneCameraMotion.PAN_RIGHT, SceneCameraMotion.fromString(plan.scenes[1].cameraMotion))
    }

    @Test
    fun aspectRatios_haveH264CompatibleDimensions() {
        VideoAspectRatio.entries.forEach { ratio ->
            assertTrue("${ratio.name} width must be divisible by 16", ratio.width % 16 == 0)
            assertTrue("${ratio.name} height must be divisible by 16", ratio.height % 16 == 0)
        }
    }

    @Test
    fun cameraMotion_fallsBackSafelyOnUnknown() {
        assertEquals(SceneCameraMotion.ZOOM_IN, SceneCameraMotion.fromString("UNKNOWN_MOVE"))
        assertEquals(SceneCameraMotion.DRIFT_UP, SceneCameraMotion.fromString("drift_up"))
    }
}
