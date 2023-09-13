package com.brandbrigade.augumentation.library

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.google.android.exoplayer2.util.GlProgram
import com.google.android.exoplayer2.util.GlUtil
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.opengles.GL10

internal class BitmapOverlayVideoProcessor(
    context: Context,
    private val liveTimestampHolder: LiveTimestampHolder
) :
    VideoProcessingGLSurfaceView.VideoProcessor {
    private val context: Context
    private val paint: Paint
    private val textures: IntArray
    private val overlayBitmap: Bitmap
    private var logoBitmap: Bitmap? = null
    private val overlayCanvas: Canvas

    private lateinit var program: GlProgram
    private var bitmapScaleX = 0f
    private var bitmapScaleY = 0f

    init {
        this.context = context.applicationContext
        paint = Paint()
        paint.textSize = 64f
        paint.isAntiAlias = true
        paint.setARGB(0xFF, 0xFF, 0xFF, 0xFF)
        textures = IntArray(1)
        overlayBitmap = Bitmap.createBitmap(OVERLAY_WIDTH, OVERLAY_HEIGHT, Bitmap.Config.ARGB_8888)
        overlayCanvas = Canvas(overlayBitmap)
        logoBitmap = try {
            (context.packageManager.getApplicationIcon(context.packageName) as BitmapDrawable)
                    .bitmap
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException(e)
        }
    }

    override fun initialize() {
        try {
            program = GlProgram(
                    context,  /* vertexShaderFilePath= */
                    "bitmap_overlay_video_processor_vertex.glsl",  /* fragmentShaderFilePath= */
                    "bitmap_overlay_video_processor_fragment.glsl"
            )
        } catch (e: IOException) {
            throw IllegalStateException(e)
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "Failed to initialize the shader program", e)
            return
        }
        program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        program.setBufferAttribute(
                "aTexCoords",
                GlUtil.getTextureCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameterf(
                GL10.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MIN_FILTER,
                GL10.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
                GL10.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MAG_FILTER,
                GL10.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT.toFloat())
        GLES20.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT.toFloat())
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D,  /* level= */0, overlayBitmap,  /* border= */0)
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        bitmapScaleX = width.toFloat() / OVERLAY_WIDTH
        bitmapScaleY = height.toFloat() / OVERLAY_HEIGHT
    }

    var prevFrameTimestamp = 0L
    override fun draw(frameTexture: Int, drawFrameTimestampUs: Long, transformMatrix: FloatArray?) {
        // Draw to the canvas and store it in a texture.
        var frameTimestampUs = liveTimestampHolder.startTimestampUs + drawFrameTimestampUs

        val delta = frameTimestampUs - prevFrameTimestamp
        prevFrameTimestamp = frameTimestampUs
        Log.d("logs_tag", "draw: ${drawFrameTimestampUs}")
        val text3 = generateFramecodeString(frameTimestampUs, liveTimestampHolder.fps)
        overlayBitmap.eraseColor(Color.TRANSPARENT)
        overlayCanvas.drawBitmap(logoBitmap!!,  /* left= */32f,  /* top= */32f, paint)
        overlayCanvas.drawText(
                text3 /* "! " + (new Random()).nextInt()*/,  /* x= */
                200f,  /* y= */
                130f,
                paint
        )

        GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[0])
        GLUtils.texSubImage2D(
                GL10.GL_TEXTURE_2D,  /* level= */0,  /* xoffset= */0,  /* yoffset= */0, overlayBitmap
        )
        try {
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "Failed to populate the texture", e)
        }

        // Run the shader program.
        program.setSamplerTexIdUniform("uTexSampler0", frameTexture,  /* texUnitIndex= */0)
        program.setSamplerTexIdUniform("uTexSampler1", textures[0],  /* texUnitIndex= */1)
        program.setFloatUniform("uScaleX", bitmapScaleX)
        program.setFloatUniform("uScaleY", bitmapScaleY)
        if (transformMatrix != null) {
            program.setFloatsUniform("uTexTransform", transformMatrix)
        }
        try {
            program.bindAttributesAndUniforms()
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "Failed to update the shader program", e)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,  /* first= */0,  /* count= */4)
        try {
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "Failed to draw a frame", e)
        }
    }

    override fun release() {
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            Log.e(TAG, "Failed to delete the shader program", e)
        }
    }

    private fun generateFramecodeString(
            timeStamp: Long,
            fps: Double
    ): String { //timecode [hh,mm,ss,ff,.1] //.1= 1|0, fps (60,59.94,30.29.96), duplicated (true|false)
        var millis = timeStamp / 1000.0
        val days = TimeUnit.MILLISECONDS.toDays(millis.toLong())
        millis -= TimeUnit.DAYS.toMillis(days)
        val hours = TimeUnit.MILLISECONDS.toHours(millis.toLong())
        millis -= TimeUnit.HOURS.toMillis(hours)
        val minutes = millis.toLong() / 1000 / 60L
        millis -= minutes * 1000 * 60
        val seconds = millis.toLong() / 1000L
        millis -= seconds * 1000L
        val secondsDouble = millis / 1000.0

        return "${minutes}:${seconds}:${(secondsDouble * fps).toInt()}"
    }

    companion object {
        private const val TAG = "BitmapOverlayVP"
        private const val OVERLAY_WIDTH = 512
        private const val OVERLAY_HEIGHT = 256
    }
}