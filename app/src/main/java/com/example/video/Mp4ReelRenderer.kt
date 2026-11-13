package com.example.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Surface
import com.example.data.SceneCameraMotion
import com.example.data.StoryboardScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.coroutines.coroutineContext

data class SceneRenderInput(
    val scene: StoryboardScene,
    val bitmap: Bitmap
)

class Mp4ReelRenderer {

    companion object {
        private const val FPS = 24
        private const val CROSSFADE_FRAMES = 12
        private const val BIT_RATE = 4_500_000
        private const val I_FRAME_INTERVAL = 1
        private const val TIMEOUT_USEC = 10_000L

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;

            uniform sampler2D uTexA;
            uniform sampler2D uTexB;
            uniform sampler2D uOverlayTex;

            uniform vec2 uCropScaleA;
            uniform float uZoomA;
            uniform vec2 uOffsetA;

            uniform vec2 uCropScaleB;
            uniform float uZoomB;
            uniform vec2 uOffsetB;

            uniform float uBlend;
            uniform float uHasOverlay;
            uniform float uVignette;

            vec2 transformUv(vec2 uv, vec2 cropScale, float zoom, vec2 panOffset) {
                vec2 centered = (uv - 0.5) * cropScale / zoom + 0.5 + panOffset;
                return clamp(centered, 0.001, 0.999);
            }

            void main() {
                vec2 uvA = transformUv(vTexCoord, uCropScaleA, uZoomA, uOffsetA);
                vec4 colorA = texture2D(uTexA, uvA);

                vec4 baseColor = colorA;
                if (uBlend > 0.001) {
                    vec2 uvB = transformUv(vTexCoord, uCropScaleB, uZoomB, uOffsetB);
                    vec4 colorB = texture2D(uTexB, uvB);
                    baseColor = mix(colorA, colorB, uBlend);
                }

                // Subtle cinema vignette
                float dist = distance(vTexCoord, vec2(0.5, 0.5));
                float vig = smoothstep(0.38, 0.95, dist) * uVignette;
                baseColor.rgb *= (1.0 - vig);

                if (uHasOverlay > 0.5) {
                    vec4 ov = texture2D(uOverlayTex, vTexCoord);
                    baseColor.rgb = mix(baseColor.rgb, ov.rgb, ov.a);
                }

                gl_FragColor = vec4(baseColor.rgb, 1.0);
            }
        """
    }

    suspend fun renderMp4(
        scenes: List<SceneRenderInput>,
        outputFile: File,
        width: Int,
        height: Int,
        burnInCaptions: Boolean,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.Default) {
        require(scenes.isNotEmpty()) { "At least one scene is required to render a video." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val sceneFrameCounts = scenes.map { (it.scene.durationSeconds.coerceIn(2, 5)) * FPS }
        val totalFrames = sceneFrameCounts.sum()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        var encoder: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
        val sceneTextures = IntArray(scenes.size)
        val overlayTextures = IntArray(scenes.size)

        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder.createInputSurface()
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val eglState = initEgl(inputSurface)
            eglDisplay = eglState.first
            eglContext = eglState.second
            eglSurface = eglState.third

            val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            GLES20.glUseProgram(program)
            GLES20.glViewport(0, 0, width, height)

            val quadVertices = floatArrayOf(
                -1f,  1f,  0f, 0f,
                -1f, -1f,  0f, 1f,
                 1f,  1f,  1f, 0f,
                 1f, -1f,  1f, 1f
            )
            val vertexBuffer: FloatBuffer = ByteBuffer
                .allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(quadVertices)
                    position(0)
                }

            val aPosition = GLES20.glGetAttribLocation(program, "aPosition")
            val aTexCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aPosition)
            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aTexCoord)

            val uTexA = GLES20.glGetUniformLocation(program, "uTexA")
            val uTexB = GLES20.glGetUniformLocation(program, "uTexB")
            val uOverlayTex = GLES20.glGetUniformLocation(program, "uOverlayTex")
            val uCropScaleA = GLES20.glGetUniformLocation(program, "uCropScaleA")
            val uZoomA = GLES20.glGetUniformLocation(program, "uZoomA")
            val uOffsetA = GLES20.glGetUniformLocation(program, "uOffsetA")
            val uCropScaleB = GLES20.glGetUniformLocation(program, "uCropScaleB")
            val uZoomB = GLES20.glGetUniformLocation(program, "uZoomB")
            val uOffsetB = GLES20.glGetUniformLocation(program, "uOffsetB")
            val uBlend = GLES20.glGetUniformLocation(program, "uBlend")
            val uHasOverlay = GLES20.glGetUniformLocation(program, "uHasOverlay")
            val uVignette = GLES20.glGetUniformLocation(program, "uVignette")

            GLES20.glGenTextures(scenes.size, sceneTextures, 0)
            val cropScales = Array(scenes.size) { FloatArray(2) }
            scenes.forEachIndexed { idx, input ->
                uploadBitmapTexture(sceneTextures[idx], input.bitmap)
                cropScales[idx] = computeCropScale(input.bitmap.width, input.bitmap.height, width, height)
            }

            if (burnInCaptions) {
                GLES20.glGenTextures(scenes.size, overlayTextures, 0)
                scenes.forEachIndexed { idx, input ->
                    val captionBmp = renderCaptionOverlay(width, height, input.scene.caption)
                    uploadBitmapTexture(overlayTextures[idx], captionBmp)
                    captionBmp.recycle()
                }
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var globalFrame = 0

            for (sceneIndex in scenes.indices) {
                val frameCount = sceneFrameCounts[sceneIndex]
                val hasNext = sceneIndex < scenes.lastIndex
                val motionA = SceneCameraMotion.fromString(scenes[sceneIndex].scene.cameraMotion)
                val motionB = if (hasNext) {
                    SceneCameraMotion.fromString(scenes[sceneIndex + 1].scene.cameraMotion)
                } else {
                    motionA
                }

                for (localFrame in 0 until frameCount) {
                    coroutineContext.ensureActive()

                    val tA = localFrame.toFloat() / (frameCount - 1).coerceAtLeast(1)
                    val (zoomA, offXA, offYA) = computeCameraTransform(motionA, tA)

                    val remaining = frameCount - 1 - localFrame
                    val blend = if (hasNext && remaining < CROSSFADE_FRAMES) {
                        val raw = 1f - (remaining.toFloat() / CROSSFADE_FRAMES.toFloat())
                        raw * raw * (3f - 2f * raw)
                    } else {
                        0f
                    }

                    GLES20.glClearColor(0f, 0f, 0f, 1f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTextures[sceneIndex])
                    GLES20.glUniform1i(uTexA, 0)
                    GLES20.glUniform2f(uCropScaleA, cropScales[sceneIndex][0], cropScales[sceneIndex][1])
                    GLES20.glUniform1f(uZoomA, zoomA)
                    GLES20.glUniform2f(uOffsetA, offXA, offYA)

                    if (hasNext && blend > 0.001f) {
                        val nextIndex = sceneIndex + 1
                        val tB = (CROSSFADE_FRAMES - 1 - remaining).toFloat() / sceneFrameCounts[nextIndex].toFloat()
                        val (zoomB, offXB, offYB) = computeCameraTransform(motionB, tB)
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTextures[nextIndex])
                        GLES20.glUniform1i(uTexB, 1)
                        GLES20.glUniform2f(uCropScaleB, cropScales[nextIndex][0], cropScales[nextIndex][1])
                        GLES20.glUniform1f(uZoomB, zoomB)
                        GLES20.glUniform2f(uOffsetB, offXB, offYB)
                    } else {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sceneTextures[sceneIndex])
                        GLES20.glUniform1i(uTexB, 1)
                        GLES20.glUniform2f(uCropScaleB, cropScales[sceneIndex][0], cropScales[sceneIndex][1])
                        GLES20.glUniform1f(uZoomB, zoomA)
                        GLES20.glUniform2f(uOffsetB, offXA, offYA)
                    }

                    GLES20.glUniform1f(uBlend, blend)
                    GLES20.glUniform1f(uVignette, 0.24f)

                    if (burnInCaptions) {
                        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextures[sceneIndex])
                        GLES20.glUniform1i(uOverlayTex, 2)
                        GLES20.glUniform1f(uHasOverlay, 1f)
                    } else {
                        GLES20.glUniform1f(uHasOverlay, 0f)
                    }

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                    val ptsNs = globalFrame * 1_000_000_000L / FPS
                    EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs)
                    EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                    val drainResult = drainEncoder(
                        encoder = encoder,
                        muxer = muxer,
                        bufferInfo = bufferInfo,
                        trackIndex = trackIndex,
                        muxerStarted = muxerStarted,
                        endOfStream = false
                    )
                    trackIndex = drainResult.first
                    muxerStarted = drainResult.second

                    globalFrame++
                    onProgress(globalFrame.toFloat() / totalFrames.toFloat())
                }
            }

            encoder.signalEndOfInputStream()
            val finalDrain = drainEncoder(
                encoder = encoder,
                muxer = muxer,
                bufferInfo = bufferInfo,
                trackIndex = trackIndex,
                muxerStarted = muxerStarted,
                endOfStream = true
            )
            muxerStarted = finalDrain.second
        } finally {
            runCatching {
                if (eglDisplay !== EGL14.EGL_NO_DISPLAY) {
                    GLES20.glDeleteTextures(sceneTextures.size, sceneTextures, 0)
                    if (burnInCaptions) {
                        GLES20.glDeleteTextures(overlayTextures.size, overlayTextures, 0)
                    }
                    EGL14.eglMakeCurrent(
                        eglDisplay,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                    )
                    if (eglSurface !== EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    }
                    if (eglContext !== EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglReleaseThread()
                    EGL14.eglTerminate(eglDisplay)
                }
            }
            runCatching { inputSurface?.release() }
            runCatching {
                encoder?.stop()
                encoder?.release()
            }
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean
    ): Pair<Int, Boolean> {
        var currentTrack = trackIndex
        var started = muxerStarted

        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (started) {
                        throw IllegalStateException("MediaCodec output format changed twice.")
                    }
                    val newFormat = encoder.outputFormat
                    currentTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    started = true
                }
                outputIndex >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(outputIndex)
                        ?: throw IllegalStateException("Encoder output buffer $outputIndex was null.")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (!started) {
                            throw IllegalStateException("Muxer has not started yet.")
                        }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrack, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
        return currentTrack to started
    }

    private fun computeCropScale(srcW: Int, srcH: Int, dstW: Int, dstH: Int): FloatArray {
        val srcAspect = srcW.toFloat() / srcH.coerceAtLeast(1).toFloat()
        val dstAspect = dstW.toFloat() / dstH.coerceAtLeast(1).toFloat()
        return if (srcAspect > dstAspect) {
            floatArrayOf(dstAspect / srcAspect, 1f)
        } else {
            floatArrayOf(1f, srcAspect / dstAspect)
        }
    }

    private fun computeCameraTransform(
        motion: SceneCameraMotion,
        t: Float
    ): Triple<Float, Float, Float> {
        // Smooth ease-in-out
        val eased = t * t * (3f - 2f * t)
        return when (motion) {
            SceneCameraMotion.ZOOM_IN -> Triple(1.04f + 0.16f * eased, 0f, 0f)
            SceneCameraMotion.ZOOM_OUT -> Triple(1.20f - 0.16f * eased, 0f, 0f)
            SceneCameraMotion.PAN_LEFT -> Triple(1.15f, 0.045f - 0.09f * eased, 0f)
            SceneCameraMotion.PAN_RIGHT -> Triple(1.15f, -0.045f + 0.09f * eased, 0f)
            SceneCameraMotion.DRIFT_UP -> Triple(1.14f + 0.04f * eased, 0f, 0.035f - 0.07f * eased)
        }
    }

    private fun renderCaptionOverlay(width: Int, height: Int, caption: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        if (caption.isBlank()) return bitmap

        val gradHeight = height * 0.28f
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, height - gradHeight,
                0f, height.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(175, 4, 20, 14)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, height - gradHeight, width.toFloat(), height.toFloat(), gradPaint)

        val textSizePx = (width.coerceAtMost(height) * 0.038f).coerceIn(22f, 36f)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 236, 253, 245)
            textSize = textSizePx
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.argb(190, 0, 0, 0))
        }

        val maxTextWidth = (width * 0.82f).toInt()
        val staticLayout = StaticLayout.Builder
            .obtain(caption, 0, caption.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .build()

        val pillPaddingX = 28f
        val pillPaddingY = 14f
        val boxWidth = (0 until staticLayout.lineCount)
            .maxOfOrNull { staticLayout.getLineWidth(it) }
            ?.plus(pillPaddingX * 2f)
            ?.coerceAtLeast(160f) ?: 200f
        val boxHeight = staticLayout.height + pillPaddingY * 2f
        val boxLeft = (width - boxWidth) / 2f
        val boxBottom = height - (height * 0.06f)
        val boxTop = boxBottom - boxHeight

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(135, 6, 40, 28)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 110, 231, 183)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rect = RectF(boxLeft, boxTop, boxLeft + boxWidth, boxBottom)
        canvas.drawRoundRect(rect, 24f, 24f, pillPaint)
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        canvas.save()
        canvas.translate((width - maxTextWidth) / 2f, boxTop + pillPaddingY)
        staticLayout.draw(canvas)
        canvas.restore()

        return bitmap
    }

    private fun uploadBitmapTexture(texId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    }

    private fun initEgl(surface: Surface): Triple<EGLDisplay, EGLContext, EGLSurface> {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display !== EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display." }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL14." }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1, // EGL_RECORDABLE_ANDROID
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribList, 0, configs, 0, configs.size, numConfigs, 0)) {
            "Unable to find suitable EGLConfig."
        }
        val config = configs[0] ?: error("EGLConfig was null.")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(context !== EGL14.EGL_NO_CONTEXT) { "Failed to create EGLContext." }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, surfaceAttribs, 0)
        check(eglSurface !== EGL14.EGL_NO_SURFACE) { "Failed to create EGLSurface." }

        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            "eglMakeCurrent failed."
        }
        return Triple(display, context, eglSurface)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Could not link GL program: $info")
        }
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Could not compile shader: $info")
        }
        return shader
    }
}
