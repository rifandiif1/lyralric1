package com.example.lyraplayer.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.lyraplayer.lyrics.LrcParser
import com.example.lyraplayer.widget.LyricsWidgetProvider

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastLyric = ""
    private var lastTitle = ""

    private val lyricTicker = object : Runnable {
        override fun run() {
            val session = mediaSession
            val player = exoPlayer
            if (session != null && player != null) {
                val prefs = getSharedPreferences("lyra", MODE_PRIVATE)
                val currentSessionId = player.audioSessionId
                if (currentSessionId > 0 && prefs.getInt("audio_session_id", 0) != currentSessionId) {
                    prefs.edit().putInt("audio_session_id", currentSessionId).apply()
                }
                val lrc = prefs.getString("current_lrc", "").orEmpty()
                val rawLyric = LrcParser.displayLine(lrc, player.currentPosition)
                val cleanLyric = rawLyric.replace(Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>"), "").trim()
                val title = prefs.getString("current_title", "Lyra Player").orEmpty()
                if (cleanLyric != lastLyric || title != lastTitle) {
                    lastLyric = cleanLyric
                    lastTitle = title
                    LyricsWidgetProvider.updateAll(
                        this@PlaybackService,
                        title,
                        cleanLyric.ifBlank { "Lirik belum tersedia" }
                    )
                }
            }
            handler.postDelayed(this, 350)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("lyra", MODE_PRIVATE)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer = player

        if (player.audioSessionId > 0) {
            prefs.edit().putInt("audio_session_id", player.audioSessionId).apply()
        }

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId > 0) {
                    prefs.edit().putInt("audio_session_id", audioSessionId).apply()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
        handler.post(lyricTicker)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        handler.removeCallbacks(lyricTicker)
        mediaSession?.run { player.release(); release() }
        exoPlayer = null
        getSharedPreferences("lyra", MODE_PRIVATE).edit().putInt("audio_session_id", 0).apply()
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (mediaSession?.player?.isPlaying != true) stopSelf()
    }
}
