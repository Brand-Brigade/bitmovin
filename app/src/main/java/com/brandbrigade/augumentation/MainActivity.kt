package com.brandbrigade.augumentation

import android.content.Intent
import android.media.MediaFormat
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.brandbrigade.augumentation.library.AugumentationExoPlayerAdapter
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager
import com.google.android.exoplayer2.drm.DrmSessionManager
import com.google.android.exoplayer2.drm.FrameworkMediaDrm
import com.google.android.exoplayer2.drm.HttpMediaDrmCallback
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoFrameMetadataListener

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    var playerView: StyledPlayerView? = null
    var player: ExoPlayer? = null
    var augumentationExoPlayerAdapter: AugumentationExoPlayerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        playerView = findViewById<StyledPlayerView>(R.id.player_view)

    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
            if (playerView != null) {
                playerView!!.onResume()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (playerView != null) {
                playerView!!.onResume()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView!!.onPause()
            }
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView!!.onPause()
            }
            releasePlayer()
        }
    }

    fun initializePlayer() {
        val intent: Intent = getIntent()
        val action = intent.action
        val uri = Uri.parse(DEFAULT_MEDIA_URI)
        val drmSessionManager: DrmSessionManager
        drmSessionManager = if (Util.SDK_INT >= 18 && intent.hasExtra(DRM_SCHEME_EXTRA)) {
            val drmScheme = Assertions.checkNotNull<String>(intent.getStringExtra(DRM_SCHEME_EXTRA))
            val drmLicenseUrl = Assertions.checkNotNull<String>(intent.getStringExtra(DRM_LICENSE_URL_EXTRA))
            val drmSchemeUuid = Assertions.checkNotNull(Util.getDrmUuid(drmScheme))
            val licenseDataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            val drmCallback = HttpMediaDrmCallback(drmLicenseUrl, licenseDataSourceFactory)
            DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(drmSchemeUuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(drmCallback)
        } else {
            DrmSessionManager.DRM_UNSUPPORTED
        }
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)
        val mediaSource: MediaSource
        val fileExtension = intent.getStringExtra(EXTENSION_EXTRA)
        val type: @C.ContentType Int = if (TextUtils.isEmpty(fileExtension)) Util.inferContentType(uri) else Util.inferContentTypeForExtension(fileExtension!!)
        mediaSource = if (type == C.CONTENT_TYPE_DASH) {
            DashMediaSource.Factory(dataSourceFactory)
                .setDrmSessionManagerProvider { unusedMediaItem: MediaItem? -> drmSessionManager }
                .createMediaSource(MediaItem.fromUri(uri))
        } else if (type == C.CONTENT_TYPE_OTHER) {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setDrmSessionManagerProvider { unusedMediaItem: MediaItem? -> drmSessionManager }
                .createMediaSource(MediaItem.fromUri(uri))
        } else if (type == C.CONTENT_TYPE_HLS) {
            val defaultHlsExtractorFactory = DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS, true)
            HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(defaultHlsExtractorFactory)
                .createMediaSource(MediaItem.fromUri(uri))
        } else {
            throw IllegalStateException()
        }
        val player = ExoPlayer.Builder(getApplicationContext()).build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
        augumentationExoPlayerAdapter = AugumentationExoPlayerAdapter(playerView!!, player, DEFAULT_FPS)
        playerView?.player = player
        this.player = player
    }



    fun releasePlayer() {
        playerView?.player = null
        if (player != null) {
            player!!.release()
            player = null
        }
    }

    companion object {
        private val DEFAULT_MEDIA_URI = "https://injecto-streams.s3-accelerate.amazonaws.com/hls_29fps/index.m3u8"
        private val DEFAULT_FPS = 29.97
        private val EXTENSION_EXTRA = "extension"
        private val DRM_SCHEME_EXTRA = "drm_scheme"
        private val DRM_LICENSE_URL_EXTRA = "drm_license_url"

        var TimelineStartTimeMs: Long = -1
    }
}