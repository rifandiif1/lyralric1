package com.example.lyraplayer.lyrics

import java.util.Locale

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    private val lineRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val wordTagRegex = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private val anyTimeTagRegex = Regex("(\\[|<)(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?(\\]|>)")

    private fun parseTimeMs(minStr: String, secStr: String, fracStr: String?): Long {
        val min = minStr.toLongOrNull() ?: 0L
        val sec = secStr.toLongOrNull() ?: 0L
        val frac = when (fracStr?.length ?: 0) {
            1 -> (fracStr?.toLongOrNull() ?: 0L) * 100L
            2 -> (fracStr?.toLongOrNull() ?: 0L) * 10L
            3 -> fracStr?.toLongOrNull() ?: 0L
            else -> 0L
        }
        return min * 60_000L + sec * 1_000L + frac
    }

    fun parse(lrcContent: String): List<LrcLine> {
        if (lrcContent.isBlank()) return emptyList()
        val entries = mutableListOf<LrcLine>()

        for (raw in lrcContent.lines()) {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) continue
            val prefixMatch = Regex("^(?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\])+").find(trimmed)
            if (prefixMatch != null) {
                val prefix = prefixMatch.value
                val body = trimmed.substring(prefix.length).trim()
                val cleanBody = body.replace(wordTagRegex, "").replace(lineRegex, "").trim()
                for (m in lineRegex.findAll(prefix)) {
                    val ms = parseTimeMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
                    entries.add(LrcLine(ms, cleanBody))
                }
            }
        }
        return entries.sortedBy { it.timeMs }
    }

    fun displayLine(lrcContent: String, positionMs: Long): String {
        if (lrcContent.isBlank()) return ""
        val parsed = parse(lrcContent)
        if (parsed.isNotEmpty()) {
            val active = parsed.lastOrNull { it.timeMs <= positionMs } ?: parsed.first()
            return active.text
        }
        return lrcContent.lines()
            .map { it.replace(wordTagRegex, "").trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("[") }
            .orEmpty()
    }

    fun timestamp(positionMs: Long): String {
        val safe = positionMs.coerceAtLeast(0L)
        val min = safe / 60_000L
        val sec = (safe % 60_000L) / 1_000L
        val cs = (safe % 1_000L) / 10L
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", min, sec, cs)
    }

    fun shift(lrcContent: String, offsetMs: Long): String {
        if (lrcContent.isBlank() || offsetMs == 0L) return lrcContent
        return anyTimeTagRegex.replace(lrcContent) { match ->
            val open = match.groupValues[1]
            val close = match.groupValues[5]
            val currentMs = parseTimeMs(
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues.getOrNull(4)
            )
            val shifted = (currentMs + offsetMs).coerceAtLeast(0L)
            val min = shifted / 60_000L
            val sec = (shifted % 60_000L) / 1_000L
            val cs = (shifted % 1_000L) / 10L
            String.format(Locale.ROOT, "%s%02d:%02d.%02d%s", open, min, sec, cs, close)
        }
    }
}
