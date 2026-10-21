package com.example.lyraplayer.playback

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class FloatingLyricService : Service() {
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        var currentLyricText: String = "♪ Lyra Karaoke"
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

        // Layout kontainer melayang dengan sudut melengkung ala Dynamic Island / Widget Modern
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(28, 14, 28, 14)
            setBackgroundColor(Color.parseColor("#CC12170E")) // Hijau tua transparan khas Lyra
        }

        val textView = TextView(this).apply {
            text = currentLyricText
            setTextColor(Color.parseColor("#B8F36B")) // Neon Lyra Green
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
        }

        container.addView(textView)
        floatingView = container

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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        // Fitur geser posisi widget dengan sentuhan jari
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

        runCatching { windowManager?.addView(floatingView, params) }
    }

    private fun updateLyric(text: String) {
        val tv = (floatingView as? LinearLayout)?.getChildAt(0) as? TextView
        tv?.text = text.ifBlank { "♪ ..." }
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
