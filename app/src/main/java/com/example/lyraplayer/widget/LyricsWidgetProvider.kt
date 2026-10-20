package com.example.lyraplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.lyraplayer.MainActivity
import com.example.lyraplayer.R

class LyricsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("lyra", Context.MODE_PRIVATE)
        val title = prefs.getString("current_title", "Lyra Player").orEmpty()
        val lrc = prefs.getString("current_lrc", "").orEmpty()
        updateAll(context, title, if (lrc.isBlank()) "Ketuk untuk membuka Lyra Player" else "♪ Memutar musik...")
    }

    companion object {
        fun updateAll(context: Context, title: String, lyric: String) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LyricsWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.lyrics_widget).apply {
                setTextViewText(R.id.widget_title, title.ifBlank { "Lyra Player" })
                setTextViewText(R.id.widget_lyric, lyric.ifBlank { "♪ Lirik belum tersedia" })
                setOnClickPendingIntent(R.id.widget_title, pendingIntent)
                setOnClickPendingIntent(R.id.widget_lyric, pendingIntent)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
