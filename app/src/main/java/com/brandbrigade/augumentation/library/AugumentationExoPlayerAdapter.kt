package com.brandbrigade.augumentation.library

import android.util.Log
import android.widget.FrameLayout
import androidx.core.view.children
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.hls.HlsManifest
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView


class AugumentationExoPlayerAdapter (
    private val playerView: StyledPlayerView,
    private var player: ExoPlayer,
    private val fps: Double
) {
    private lateinit var videoProcessingGlSurfaceView: VideoProcessingGLSurfaceView
    private lateinit var bitmapOverlayVideoProcessor: BitmapOverlayVideoProcessor
    private val liveTimestampHolder = LiveTimestampHolder()

    init {
        createVideoProcessingGlSurfaceView()
        player.addListener(object: Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val windowIndex = player.currentWindowIndex
                val playerTimeLine = player.currentTimeline
                if (windowIndex >= 0 && playerTimeLine != null) {
                    val manifest = player.currentManifest
                    if (manifest != null && manifest is HlsManifest && liveTimestampHolder.startTimestampUs <= 0) {
                        liveTimestampHolder.startTimestampUs = (manifest as HlsManifest).mediaPlaylist.startTimeUs
                        liveTimestampHolder.fps = fps
                    }
                }
                super.onTimelineChanged(timeline, reason)
            }

            override fun onMetadata(metadata: Metadata) {
                Log.d("logs_tag", "onMetadata: ")
                try {
                    android.util.Log.d("logs_tag", "onMetadata")
                    val timeline = player!!.currentTimeline
                    val manifest = player.currentManifest
                    val window = timeline.getWindow(0, Timeline.Window())
                    android.util.Log.d("logs_tag", "onMetadata: ${window.windowStartTimeMs} ${window.presentationStartTimeMs}")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                super.onMetadata(metadata)
            }
        })
    }

    private fun createVideoProcessingGlSurfaceView() {
        bitmapOverlayVideoProcessor = BitmapOverlayVideoProcessor(playerView.context, liveTimestampHolder)
        videoProcessingGlSurfaceView = VideoProcessingGLSurfaceView(
            playerView.context, false, bitmapOverlayVideoProcessor)
        val contentFrame = playerView.children.first { it is AspectRatioFrameLayout } as FrameLayout

        contentFrame.addView(videoProcessingGlSurfaceView)
        videoProcessingGlSurfaceView.setPlayer(player)
    }

}