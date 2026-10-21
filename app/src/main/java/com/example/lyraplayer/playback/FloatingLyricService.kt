package com.example.lyraplayer.playback

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.lyraplayer.R

class FloatingLyricService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        var currentLyricText: String = "Lyra Karaoke"
            set(value) {
                field = value
                instance?.updateLyric(value)
            }
        private var instance: FloatingLyricService? = null
        var isRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Buat tampilan dari layout sederhana atau TextView programmatically
        val textView = TextView(this).apply {
            text = currentLyricText
            setTextColor(android.graphics.Color.parseColor("#B8F36B")) // Warna neon khas Lyra
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(24, 12, 24, 12)
            setBackgroundColor(android.graphics.Color.parseColor("#99000000")) // Hitam transparan
        }
        
        floatingView = textView

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        // Fitur Geser (Drag) Floating Window dengan sentuhan jari
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (_: Exception) {}
    }

    private fun updateLyric(text: String) {
        (floatingView as? TextView)?.text = text.ifBlank { "â™ª..." }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        if (floatingView != null) {
            runCatching { windowManager?.removeView(floatingView) }
            floatingView = null
        }
    }
}
