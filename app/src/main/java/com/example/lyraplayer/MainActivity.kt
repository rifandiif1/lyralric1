package com.example.lyraplayer

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaRecorder
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.lyraplayer.lyrics.AudioTagEditor
import com.example.lyraplayer.lyrics.LrcParser
import com.example.lyraplayer.lyrics.LrcResult
import com.example.lyraplayer.lyrics.LrclibClient
import com.example.lyraplayer.lyrics.YtmClient
import com.example.lyraplayer.lyrics.YtmTrackItem
import com.example.lyraplayer.lyrics.YtmHomeSection
import com.example.lyraplayer.playback.PlaybackService
import com.example.lyraplayer.widget.LyricsWidgetProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt


// === 1. MODELS, ENUMS & HELPERS ===
private data class AudioTrack(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val dateAddedSec: Long = 0L,
    val filePath: String = "",
    val folder: String = "",
    val isSaf: Boolean = false,
    val artworkUri: Uri? = null
)

private data class MetadataOverride(val title: String, val artist: String, val album: String, val artworkUri: String = "")
private data class LyraPlaylist(val name: String, val trackIds: List<String>)
private data class KaraokeWord(val text: String, val startMs: Long, val endMs: Long)

private data class KaraokeLine(
    val startMs: Long,
    val endMs: Long,
    val plainText: String,
    val words: List<KaraokeWord>,
    val isEnhanced: Boolean,
    val translationText: String = ""
)

private enum class LyraTab(val label: String) { HOME("Home"), NOW_PLAYING("Putar"), LIBRARY("Musik"), LYRICS("Lirik"), SETTINGS("Setelan") }
private enum class LibrarySubTab(val title: String) { ALL("Semua"), YT_MUSIC("YT Music"), FOLDERS("Folder"), FAVORITES("Favorit"), PLAYLISTS("Playlist"), QUEUE("Antrean") }

private enum class SortOption(val label: String) {
    DATE_ADDED_DESC("Terbaru Ditambahkan"),
    DATE_ADDED_ASC("Terlama Ditambahkan"),
    TITLE_ASC("Judul A-Z"),
    TITLE_DESC("Judul Z-A"),
    ARTIST_ASC("Artis A-Z"),
    DURATION_DESC("Terpanjang"),
    DURATION_ASC("Terpendek"),
    MOST_PLAYED("Sering Diputar")
}

private enum class VisualizerStyle(val label: String) {
    NEON_BARS("Neon Bars"),
    MIRROR_WAVE("Mirror Wave"),
    CYBER_DOTS("Cyber Dots"),
    PULSE_LINE("Pulse Line")
}

private enum class ThemeAccent(val label: String, val primaryHex: Long, val darkHex: Long) {
    DYNAMIC("Dynamic Auto", 0xFFB8F36B, 0xFF283A18),
    LYRA_NEON("Lyra Neon", 0xFFB8F36B, 0xFF283A18),
    CYBER_CYAN("Cyber Cyan", 0xFF5CE1E6, 0xFF103238),
    SUNSET_ROSE("Sunset Rose", 0xFFFF7597, 0xFF3B1622),
    ROYAL_GOLD("Royal Gold", 0xFFFFD166, 0xFF3A2D10),
    ELECTRIC_VIOLET("Electric Violet", 0xFFB388FF, 0xFF26183D)
}

private enum class LyricsSubTab(val title: String) { PREVIEW("♪ Karaoke"), SEARCH("⌕ Cari Online"), EDITOR("✎ Studio LRC") }
private enum class LyraIconType { HOME, PLAY, PAUSE, NEXT, PREV, SHUFFLE, REPEAT_OFF, REPEAT_ALL, REPEAT_ONE, HEART_FILLED, HEART_OUTLINE, EDIT, PLUS, QUEUE, MUSIC, LYRICS, SETTINGS, SEARCH }

private fun resolveThemeColors(preset: ThemeAccent, songTitle: String): Pair<Color, Color> {
    if (preset != ThemeAccent.DYNAMIC) return Pair(Color(preset.primaryHex), Color(preset.darkHex))
    val palettes = listOf(
        Pair(Color(0xFFB8F36B), Color(0xFF283A18)),
        Pair(Color(0xFF5CE1E6), Color(0xFF103238)),
        Pair(Color(0xFFFF7597), Color(0xFF3B1622)),
        Pair(Color(0xFFFFD166), Color(0xFF3A2D10)),
        Pair(Color(0xFFB388FF), Color(0xFF26183D))
    )
    return palettes[abs(songTitle.hashCode()) % palettes.size]
}

private fun lyricsCacheKey(title: String, artist: String): String =
    (title.trim().substringBeforeLast('.', title.trim()) + "|" + artist.trim()).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
}

private fun formatAddedDate(sec: Long): String {
    if (sec <= 0L) return ""
    return runCatching { SimpleDateFormat("dd MMM yy", Locale.getDefault()).format(Date(sec * 1000L)) }.getOrDefault("")
}

private fun tryReadLocalSidecarLrc(filePath: String): String? {
    if (filePath.isBlank()) return null
    return runCatching {
        val audioFile = File(filePath)
        val parent = audioFile.parentFile ?: return null
        val base = audioFile.nameWithoutExtension
        val candidates = listOf(File(parent, "$base.lrc"), File(parent, "$base.LRC"))
        candidates.firstOrNull { it.exists() && it.canRead() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun cleanSearchQuery(rawTitle: String, rawArtist: String): Pair<String, String> {
    var t = rawTitle.trim().substringBeforeLast('.', rawTitle.trim())
    var a = rawArtist.trim().takeUnless { it.equals("Artis tidak diketahui", true) || it.equals("<unknown>", true) || it.equals("unknown", true) }.orEmpty()
    t = t.replace(Regex("(?i)\\[.*?(official|lyric|audio|video|mv|hd|hq|visualizer|karaoke|ost|remaster).*?\\]"), "")
        .replace(Regex("(?i)\\(.*?(official|lyric|audio|video|mv|hd|hq|visualizer|karaoke|ost|remaster).*?\\)"), "")
        .replace(Regex("(?i)\\s*[-|]\\s*(official\\s*)?(music\\s*)?(audio|video|lyrics?|visualizer|mv|karaoke).*?$"), "")
        .replace(Regex("(?i)\\b(ft\\.?|feat\\.?)\\s+[^-()\\[\\]]+"), "").replace(Regex("_+"), " ").trim()
    if (a.isBlank() && t.contains(" - ")) {
        val p = t.split(" - ", limit = 2)
        if (p.size == 2 && p[0].length in 2..40 && p[1].length >= 2) { a = p[0].trim(); t = p[1].trim() }
    } else if (a.isNotBlank() && t.lowercase(Locale.ROOT).startsWith(a.lowercase(Locale.ROOT) + " - ")) {
        t = t.drop(a.length + 3).trim()
    }
    return Pair(t.ifBlank { rawTitle.trim() }, a)
}

// ============================================================================
// LYRICSIFY.COM EXTRACTOR & ENHANCED LRC PRIORITY ENGINE
// ============================================================================
private object LyricsifyExtractor {
    private const val BASE_URL = "https://www.lyricsify.com"
    private val enhancedCheckRegex = Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>|\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\].*?\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\]")

    fun hasEnhancedKaraokeTags(lrc: String?): Boolean {
        if (lrc.isNullOrBlank()) return false
        return enhancedCheckRegex.containsMatchIn(lrc)
    }

    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 9000
            readTimeout = 9000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        }
        return try {
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#10;", "\n")
            .replace("&#13;", "")
    }

    private fun extractLrcFromHtml(html: String): String? {
        // 1. Cari blok div id="entry" atau div class="entry" khas Lyricsify
        val blockPatterns = listOf(
            Regex("(?is)<div[^>]*id=[\"']entry[\"'][^>]*>(.*?)</div>"),
            Regex("(?is)<div[^>]*id=[\"']lyrics[_-]?body[\"'][^>]*>(.*?)</div>"),
            Regex("(?is)<pre[^>]*>(.*?)</pre>"),
            Regex("(?is)<textarea[^>]*>(.*?)</textarea>")
        )
        for (pat in blockPatterns) {
            for (m in pat.findAll(html)) {
                val decoded = decodeHtmlEntities(m.groupValues[1])
                val lrcLines = decoded.lines().map { it.trim() }.filter { it.matches(Regex("^\\[\\d{1,2}:\\d{2}.*")) || it.matches(Regex("^\\[(?:ti|ar|al|by|offset):.*", RegexOption.IGNORE_CASE)) }
                if (lrcLines.size >= 3) return lrcLines.joinToString("\n")
            }
        }
        // 2. Fallback: scan seluruh halaman untuk baris yang memiliki timestamp [mm:ss.xx]
        val fullDecoded = decodeHtmlEntities(html)
        val allSyncedLines = fullDecoded.lines().map { it.trim() }.filter {
            it.matches(Regex("^\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\].*"))
        }
        if (allSyncedLines.size >= 4) return allSyncedLines.joinToString("\n")
        return null
    }

    fun fetchFromDirectUrl(pageUrl: String, fallbackTitle: String, fallbackArtist: String): LrcResult? {
        return runCatching {
            val html = httpGet(pageUrl)
            val lrc = extractLrcFromHtml(html) ?: return null
            val pageTitle = Regex("(?is)<title>(.*?)</title>").find(html)?.groupValues?.getOrNull(1)
                ?.let { decodeHtmlEntities(it) }
                ?.replace(Regex("(?i)\\s*[-|]?\\s*lyricsify.*"), "")
                ?.replace(Regex("(?i)^lirik\\s+lagu\\s+"), "")
                ?.trim()
                .orEmpty()
            val isEnh = hasEnhancedKaraokeTags(lrc)
            LrcResult(
                name = pageTitle.ifBlank { fallbackTitle.ifBlank { "Lyricsify Track" } },
                artist = fallbackArtist.ifBlank { "Lyricsify" },
                album = if (isEnh) "Lyricsify • Enhanced LRC ★" else "Lyricsify.com",
                syncedLyrics = lrc,
                plainLyrics = EnhancedLrcEngine.stripEnhancedTags(lrc)
            )
        }.getOrNull()
    }

    fun search(title: String, artist: String): List<LrcResult> {
        val query = "$artist $title".trim()
        if (query.isBlank()) return emptyList()
        return runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchHtml = httpGet("$BASE_URL/search?q=$encoded")
            val linkRegex = Regex("(?i)href=[\"'](/lyrics/[^\"'?#]+|/lrc/[^\"'?#]+)[\"'][^>]*>(.*?)</a>")
            val candidates = linkedMapOf<String, String>()
            for (m in linkRegex.findAll(searchHtml)) {
                val path = m.groupValues[1]
                val label = decodeHtmlEntities(m.groupValues[2]).trim()
                if (!candidates.containsKey(path) && label.length > 2) {
                    candidates[path] = label
                }
                if (candidates.size >= 4) break
            }
            val foundResults = mutableListOf<LrcResult>()
            for ((path, label) in candidates) {
                val fullUrl = if (path.startsWith("http")) path else "$BASE_URL$path"
                val pageHtml = runCatching { httpGet(fullUrl) }.getOrDefault("")
                val lrc = extractLrcFromHtml(pageHtml)
                if (!lrc.isNullOrBlank()) {
                    val cleanLabel = label.replace(Regex("(?i)^lirik\\s+lagu\\s+"), "").trim()
                    val splitArtist = if (cleanLabel.contains(" - ")) cleanLabel.substringBefore(" - ").trim() else artist.ifBlank { "Lyricsify" }
                    val splitTitle = if (cleanLabel.contains(" - ")) cleanLabel.substringAfter(" - ").trim() else cleanLabel.ifBlank { title }
                    val isEnh = hasEnhancedKaraokeTags(lrc)
                    foundResults.add(
                        LrcResult(
                            name = splitTitle,
                            artist = splitArtist,
                            album = if (isEnh) "Lyricsify • Enhanced LRC ★" else "Lyricsify.com",
                            syncedLyrics = lrc,
                            plainLyrics = EnhancedLrcEngine.stripEnhancedTags(lrc)
                        )
                    )
                }
            }
            foundResults
        }.getOrDefault(emptyList())
    }
}

// Pencarian gabungan (Lyricsify.com + LRCLIB) dengan Prioritas #1 untuk Enhanced LRC Karaoke
private suspend fun smartLyricsSearch(rawTitle: String, rawArtist: String): List<LrcResult> = withContext(Dispatchers.IO) {
    val trimmedInput = rawTitle.trim()
    // Jika pengguna memasukkan URL langsung dari Lyricsify.com
    if (trimmedInput.startsWith("http://", true) || trimmedInput.startsWith("https://", true) || trimmedInput.contains("lyricsify.com/", true)) {
        val url = if (trimmedInput.startsWith("http", true)) trimmedInput else "https://$trimmedInput"
        val direct = LyricsifyExtractor.fetchFromDirectUrl(url, rawTitle, rawArtist)
        if (direct != null) return@withContext listOf(direct)
    }

    val (cleanT, cleanA) = cleanSearchQuery(rawTitle, rawArtist)
    val merged = linkedMapOf<String, LrcResult>()

    fun collect(list: List<LrcResult>) {
        for (item in list) {
            if (!item.syncedLyrics.isNullOrBlank() || !item.plainLyrics.isNullOrBlank()) {
                val itemName = item.name.orEmpty().lowercase(Locale.ROOT)
                val itemArtist = item.artist.orEmpty().lowercase(Locale.ROOT)
                val isEnh = LyricsifyExtractor.hasEnhancedKaraokeTags(item.syncedLyrics)
                val fromLyricsify = item.album.orEmpty().contains("Lyricsify", true)
                val k = "$itemName|$itemArtist|$isEnh|$fromLyricsify"
                if (!merged.containsKey(k)) merged[k] = item
            }
        }
    }

    // Jalankan pencarian ke Lyricsify.com dan LRCLIB secara paralel
    coroutineScope {
        val lyricsifyDeferred = async { runCatching { LyricsifyExtractor.search(cleanT, cleanA) }.getOrDefault(emptyList()) }
        val lrclibPrimaryDeferred = async {
            if (cleanT.isNotBlank() && cleanA.isNotBlank()) runCatching { LrclibClient.search(cleanT, cleanA) }.getOrDefault(emptyList())
            else emptyList()
        }
        collect(lyricsifyDeferred.await())
        collect(lrclibPrimaryDeferred.await())
    }

    // Jika belum menemukan Enhanced LRC, perluas pencarian dengan judul saja
    if (merged.values.none { LyricsifyExtractor.hasEnhancedKaraokeTags(it.syncedLyrics) } && cleanT.isNotBlank()) {
        coroutineScope {
            val lrcFallback = async { runCatching { LrclibClient.search(cleanT, "") }.getOrDefault(emptyList()) }
            val lyfFallback = async {
                if (cleanA.isNotBlank()) runCatching { LyricsifyExtractor.search(cleanT, "") }.getOrDefault(emptyList())
                else emptyList()
            }
            collect(lyfFallback.await())
            collect(lrcFallback.await())
        }
    }

    if (merged.isEmpty() && rawTitle.isNotBlank() && !rawTitle.equals(cleanT, true)) {
        runCatching { LrclibClient.search(rawTitle.trim(), "") }.getOrNull()?.let { collect(it) }
    }

    // Urutkan dengan Prioritas Mutlak:
    // 1. Enhanced LRC (<mm:ss.xx> / Word-by-Word Karaoke)
    // 2. Hasil dari Lyricsify.com / Kecocokan Judul
    // 3. Synced LRC biasa ([mm:ss.xx])
    // 4. Plain Lyrics
    merged.values.sortedWith(
        compareByDescending<LrcResult> { LyricsifyExtractor.hasEnhancedKaraokeTags(it.syncedLyrics) }
            .thenByDescending { !it.syncedLyrics.isNullOrBlank() }
            .thenByDescending { it.album.orEmpty().contains("Lyricsify", true) }
            .thenByDescending { it.name.orEmpty().equals(cleanT, true) }
    )
}

private fun JSONArray?.toStringList(): List<String> {
    val arr = this ?: return emptyList()
    val res = mutableListOf<String>()
    for (i in 0 until arr.length()) { val s = arr.optString(i); if (!s.isNullOrBlank() && s != "null") res.add(s) }
    return res
}

private fun loadPlaylists(raw: String): List<LyraPlaylist> = runCatching {
    val arr = JSONArray(raw); val list = mutableListOf<LyraPlaylist>()
    for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); list.add(LyraPlaylist(o.optString("name", "Playlist"), o.optJSONArray("ids").toStringList())) }
    list
}.getOrDefault(emptyList())

private fun serializePlaylists(list: List<LyraPlaylist>): JSONArray {
    val arr = JSONArray()
    for (p in list) arr.put(JSONObject().put("name", p.name).put("ids", JSONArray(p.trackIds)))
    return arr
}

private fun loadCounts(raw: String): Map<String, Int> = runCatching {
    val o = JSONObject(raw); val m = mutableMapOf<String, Int>()
    val keys = o.keys(); while (keys.hasNext()) { val k = keys.next(); m[k] = o.optInt(k, 0) }
    m
}.getOrDefault(emptyMap())

private fun serializeCounts(map: Map<String, Int>): JSONObject {
    val o = JSONObject(); for ((k, v) in map.entries) o.put(k, v); return o
}

private fun loadMetadataOverrides(raw: String): Map<String, MetadataOverride> = runCatching {
    val o = JSONObject(raw); val m = mutableMapOf<String, MetadataOverride>()
    val keys = o.keys(); while (keys.hasNext()) { val k = keys.next(); val it = o.getJSONObject(k); m[k] = MetadataOverride(it.optString("title", ""), it.optString("artist", ""), it.optString("album", ""), it.optString("artworkUri", "")) }
    m
}.getOrDefault(emptyMap())

private fun saveMetadataOverrides(prefs: SharedPreferences, map: Map<String, MetadataOverride>) {
    val root = JSONObject()
    for ((k, v) in map.entries) {
        if (k.isNotBlank()) root.put(k, JSONObject().put("title", v.title).put("artist", v.artist).put("album", v.album).put("artworkUri", v.artworkUri))
    }
    prefs.edit().putString("metadata_overrides_json", root.toString()).commit()
}

// === 2. ENHANCED LRC KARAOKE & DUAL-LANGUAGE ENGINE ===
private object EnhancedLrcEngine {
    private val lineTsRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val wordTsRegex = Regex("<(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?>([^<]*)")
    private val inlineBrRegex = Regex("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]([^\\[]*)")
    private val anyTagRegex = Regex("(\\[|<)(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?(\\]|>)")

    private fun toMs(mStr: String, sStr: String, fStr: String?): Long {
        val m = mStr.toLongOrNull() ?: 0L; val s = sStr.toLongOrNull() ?: 0L
        val f = when (fStr?.length ?: 0) { 1 -> (fStr?.toLongOrNull() ?: 0L) * 100L; 2 -> (fStr?.toLongOrNull() ?: 0L) * 10L; 3 -> fStr?.toLongOrNull() ?: 0L; else -> 0L }
        return m * 60_000L + s * 1_000L + f
    }

    fun stripEnhancedTags(raw: String): String =
        raw.replace(Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>"), "")
            .replace(Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\]"), "")
            .trim()

    fun formatLineTag(ms: Long): String { val s = ms.coerceAtLeast(0L); return String.format(Locale.ROOT, "[%02d:%02d.%02d]", s / 60_000L, (s % 60_000L) / 1_000L, (s % 1_000L) / 10L) }
    fun formatWordTag(ms: Long): String { val s = ms.coerceAtLeast(0L); return String.format(Locale.ROOT, "<%02d:%02d.%02d>", s / 60_000L, (s % 60_000L) / 1_000L, (s % 1_000L) / 10L) }

    fun shiftAllTags(lrc: String, deltaMs: Long): String = anyTagRegex.replace(lrc) { m ->
        val n = (toMs(m.groupValues[2], m.groupValues[3], m.groupValues.getOrNull(4)) + deltaMs).coerceAtLeast(0L)
        String.format(Locale.ROOT, "%s%02d:%02d.%02d%s", m.groupValues[1], n / 60_000L, (n % 60_000L) / 1_000L, (n % 1_000L) / 10L, m.groupValues[5])
    }

    private fun splitDualTranslation(rawBody: String): Pair<String, String> {
        if (rawBody.contains(" // ")) {
            val p = rawBody.split(" // ", limit = 2)
            return Pair(p[0].trim(), stripEnhancedTags(p[1]))
        }
        if (rawBody.contains(" | ")) {
            val p = rawBody.split(" | ", limit = 2)
            return Pair(p[0].trim(), stripEnhancedTags(p[1]))
        }
        return Pair(rawBody, "")
    }

    fun parse(lrcText: String, enableAutoWord: Boolean): List<KaraokeLine> {
        if (lrcText.isBlank()) return emptyList()
        val rawEntries = mutableListOf<Pair<Long, String>>()
        for (rawLine in lrcText.lines()) {
            val tr = rawLine.trim(); if (tr.isBlank()) continue
            val pm = Regex("^(?:\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\])+").find(tr)
            if (pm != null) {
                val body = tr.drop(pm.value.length).trim()
                for (m in lineTsRegex.findAll(pm.value)) rawEntries.add(Pair(toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3)), body))
            }
        }
        if (rawEntries.isEmpty()) {
            return lrcText.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("[") }.mapIndexed { i, txt ->
                val s = i * 4000L; val e = s + 3800L
                val (mainTxt, transTxt) = splitDualTranslation(txt)
                KaraokeLine(s, e, mainTxt, if (enableAutoWord) interpolateWords(mainTxt, s, e) else emptyList(), false, transTxt)
            }
        }

        val groupedByTime = linkedMapOf<Long, MutableList<String>>()
        for ((ms, body) in rawEntries.sortedBy { it.first }) {
            if (body.isNotBlank()) groupedByTime.getOrPut(ms) { mutableListOf() }.add(body)
        }
        val mergedEntries = groupedByTime.entries.map { (ms, bodies) ->
            val primaryBody = bodies.first()
            val extraTrans = if (bodies.size > 1) stripEnhancedTags(bodies.drop(1).joinToString(" · ")) else ""
            Triple(ms, primaryBody, extraTrans)
        }

        return mergedEntries.mapIndexed { idx, triple ->
            val startMs = triple.first
            val (body, inlineTrans) = splitDualTranslation(triple.second)
            val finalTrans = if (triple.third.isNotBlank()) triple.third else inlineTrans
            val lineEnd = maxOf(startMs + 600L, mergedEntries.getOrNull(idx + 1)?.first ?: (startMs + 5000L))
            val angleMatches = wordTsRegex.findAll(body).toList()
            if (angleMatches.isNotEmpty()) {
                val words = mutableListOf<KaraokeWord>()
                val lead = body.substringBefore("<").trim()
                val firstMs = toMs(angleMatches.first().groupValues[1], angleMatches.first().groupValues[2], angleMatches.first().groupValues.getOrNull(3))
                if (lead.isNotEmpty()) words.add(KaraokeWord("$lead ", startMs, maxOf(startMs + 80L, firstMs)))
                for (i in angleMatches.indices) {
                    val m = angleMatches[i]; val wS = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
                    val nextW = if (i + 1 < angleMatches.size) toMs(angleMatches[i + 1].groupValues[1], angleMatches[i + 1].groupValues[2], angleMatches[i + 1].groupValues.getOrNull(3)) else lineEnd
                    val rawWordText = m.groupValues[4]
                    if (rawWordText.isNotEmpty()) {
                        val nextStartsWithSpace = angleMatches.getOrNull(i + 1)?.groupValues?.getOrNull(4)?.startsWith(" ") == true
                        val normalizedWord = if (!rawWordText.endsWith(" ") && !nextStartsWithSpace && i < angleMatches.lastIndex && rawWordText.length > 1) "$rawWordText " else rawWordText
                        words.add(KaraokeWord(normalizedWord, wS, maxOf(wS + 60L, nextW)))
                    }
                }
                KaraokeLine(startMs, lineEnd, words.joinToString("") { it.text }.trim(), words, true, finalTrans)
            } else if (lineTsRegex.containsMatchIn(body)) {
                val matches = inlineBrRegex.findAll("${formatLineTag(startMs)}$body").toList()
                val words = matches.mapIndexedNotNull { i, m ->
                    val wS = toMs(m.groupValues[1], m.groupValues[2], m.groupValues.getOrNull(3))
                    val nextW = if (i + 1 < matches.size) toMs(matches[i + 1].groupValues[1], matches[i + 1].groupValues[2], matches[i + 1].groupValues.getOrNull(3)) else lineEnd
                    val rawWordText = m.groupValues[4]
                    if (rawWordText.isNotEmpty()) KaraokeWord(rawWordText, wS, maxOf(wS + 60L, nextW)) else null
                }
                KaraokeLine(startMs, lineEnd, words.joinToString("") { it.text }.trim(), words, true, finalTrans)
            } else {
                val clean = stripEnhancedTags(body)
                KaraokeLine(startMs, lineEnd, clean, if (enableAutoWord) interpolateWords(clean, startMs, lineEnd) else emptyList(), false, finalTrans)
            }
        }
    }

    private fun interpolateWords(text: String, startMs: Long, endMs: Long): List<KaraokeWord> {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val effDur = (endMs - startMs).coerceAtLeast(400L) * 88 / 100
        val totalChars = tokens.sumOf { it.length.coerceAtLeast(1) }.coerceAtLeast(1)
        var cur = startMs
        return tokens.mapIndexed { idx, token ->
            val dur = (effDur * (token.length.coerceAtLeast(1).toFloat() / totalChars)).toLong().coerceAtLeast(60L)
            val wEnd = if (idx == tokens.lastIndex) startMs + effDur else (cur + dur)
            val w = KaraokeWord(if (idx < tokens.lastIndex) "$token " else token, cur, maxOf(cur + 40L, wEnd))
            cur = wEnd; w
        }
    }
}

// === 3. CUSTOM VECTOR ICONS & UI COMPONENTS ===
@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(context, enabled) {
        val window = (context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun SpinningVinylStage(
    artworkUri: Uri?,
    albumTitle: String,
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "vinylRot")
    val angle by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "vAng")
    val tonearmAngle by animateFloatAsState(if (isPlaying) 22f else 0f, tween(500), label = "tArm")

    Box(modifier = modifier.clickable { onClick() }, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(204.dp)
                .graphicsLayer { rotationZ = if (isPlaying) angle else 0f }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF2B2B2B), Color(0xFF0E0E0E), Color(0xFF222222), Color(0xFF080808))))
                .border(3.dp, accentColor.copy(alpha = 0.45f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    modifier = Modifier.size(94.dp).clip(CircleShape).border(2.dp, accentColor, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                LyraBrandLogo(size = 84.dp, cornerRadius = 42.dp)
            }
            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF0B0D0A)).border(2.dp, accentColor, CircleShape))
        }

        Canvas(
            modifier = Modifier
                .size(116.dp)
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 18.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.8f, 0.2f)
                    rotationZ = tonearmAngle
                }
        ) {
            val pivot = Offset(size.width * 0.8f, size.height * 0.2f)
            drawCircle(accentColor, radius = 14f, center = pivot)
            drawLine(Color.LightGray, pivot, Offset(size.width * 0.35f, size.height * 0.78f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawRoundRect(accentColor, Offset(size.width * 0.26f, size.height * 0.74f), Size(22f, 14f), CornerRadius(4f, 4f))
        }

        Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) {
            Text("${albumTitle.ifBlank { "VINYL STUDIO" }.uppercase()} • KETUK KE LIRIK ♪", color = accentColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun LyraVectorIcon(type: LyraIconType, tint: Color = Color.White, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val stroke = Stroke(width = w * 0.095f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (type) {
            LyraIconType.PLAY -> drawPath(Path().apply { moveTo(w * 0.28f, h * 0.18f); lineTo(w * 0.80f, h * 0.50f); lineTo(w * 0.28f, h * 0.82f); close() }, tint)
            LyraIconType.PAUSE -> {
                drawRoundRect(tint, Offset(w * 0.24f, h * 0.18f), Size(w * 0.18f, h * 0.64f), CornerRadius(w * 0.05f))
                drawRoundRect(tint, Offset(w * 0.58f, h * 0.18f), Size(w * 0.18f, h * 0.64f), CornerRadius(w * 0.05f))
            }
            LyraIconType.NEXT -> {
                drawPath(Path().apply { moveTo(w * 0.18f, h * 0.20f); lineTo(w * 0.64f, h * 0.50f); lineTo(w * 0.18f, h * 0.80f); close() }, tint)
                drawRoundRect(tint, Offset(w * 0.70f, h * 0.20f), Size(w * 0.12f, h * 0.60f), CornerRadius(w * 0.04f))
            }
            LyraIconType.PREV -> {
                drawRoundRect(tint, Offset(w * 0.18f, h * 0.20f), Size(w * 0.12f, h * 0.60f), CornerRadius(w * 0.04f))
                drawPath(Path().apply { moveTo(w * 0.82f, h * 0.20f); lineTo(w * 0.36f, h * 0.50f); lineTo(w * 0.82f, h * 0.80f); close() }, tint)
            }
            LyraIconType.SHUFFLE -> {
                drawPath(Path().apply { moveTo(w * 0.14f, h * 0.30f); cubicTo(w * 0.42f, h * 0.30f, w * 0.48f, h * 0.70f, w * 0.82f, h * 0.70f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.14f, h * 0.70f); cubicTo(w * 0.42f, h * 0.70f, w * 0.48f, h * 0.30f, w * 0.82f, h * 0.30f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.70f, h * 0.18f); lineTo(w * 0.86f, h * 0.30f); lineTo(w * 0.70f, h * 0.42f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.70f, h * 0.58f); lineTo(w * 0.86f, h * 0.70f); lineTo(w * 0.70f, h * 0.82f) }, tint, style = stroke)
            }
            LyraIconType.REPEAT_OFF, LyraIconType.REPEAT_ALL, LyraIconType.REPEAT_ONE -> {
                drawPath(Path().apply { moveTo(w * 0.20f, h * 0.46f); lineTo(w * 0.20f, h * 0.36f); cubicTo(w * 0.20f, h * 0.25f, w * 0.28f, h * 0.22f, w * 0.38f, h * 0.22f); lineTo(w * 0.78f, h * 0.22f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.66f, h * 0.12f); lineTo(w * 0.82f, h * 0.22f); lineTo(w * 0.66f, h * 0.32f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.80f, h * 0.54f); lineTo(w * 0.80f, h * 0.64f); cubicTo(w * 0.80f, h * 0.75f, w * 0.72f, h * 0.78f, w * 0.62f, h * 0.78f); lineTo(w * 0.22f, h * 0.78f) }, tint, style = stroke)
                drawPath(Path().apply { moveTo(w * 0.34f, h * 0.68f); lineTo(w * 0.18f, h * 0.78f); lineTo(w * 0.34f, h * 0.88f) }, tint, style = stroke)
                if (type == LyraIconType.REPEAT_ONE) {
                    drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.50f))
                    drawLine(Color(0xFF0B0D0A), Offset(w * 0.50f, h * 0.40f), Offset(w * 0.50f, h * 0.60f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
                }
            }
            LyraIconType.HEART_FILLED, LyraIconType.HEART_OUTLINE -> {
                val p = Path().apply {
                    moveTo(w * 0.50f, h * 0.82f)
                    cubicTo(w * 0.15f, h * 0.60f, w * 0.12f, h * 0.26f, w * 0.32f, h * 0.20f)
                    cubicTo(w * 0.44f, h * 0.16f, w * 0.50f, h * 0.28f, w * 0.50f, h * 0.32f)
                    cubicTo(w * 0.50f, h * 0.28f, w * 0.56f, h * 0.16f, w * 0.68f, h * 0.20f)
                    cubicTo(w * 0.88f, h * 0.26f, w * 0.85f, h * 0.60f, w * 0.50f, h * 0.82f); close()
                }
                if (type == LyraIconType.HEART_FILLED) drawPath(p, tint) else drawPath(p, tint, style = stroke)
            }
            LyraIconType.EDIT -> drawPath(Path().apply { moveTo(w * 0.20f, h * 0.80f); lineTo(w * 0.34f, h * 0.76f); lineTo(w * 0.78f, h * 0.32f); lineTo(w * 0.68f, h * 0.22f); lineTo(w * 0.24f, h * 0.66f); close() }, tint, style = stroke)
            LyraIconType.PLUS -> {
                drawLine(tint, Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.78f), w * 0.10f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.78f, h * 0.50f), w * 0.10f, StrokeCap.Round)
            }
            LyraIconType.QUEUE -> {
                drawLine(tint, Offset(w * 0.20f, h * 0.28f), Offset(w * 0.80f, h * 0.28f), w * 0.09f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), w * 0.09f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.20f, h * 0.72f), Offset(w * 0.60f, h * 0.72f), w * 0.09f, StrokeCap.Round)
            }
            LyraIconType.HOME -> drawPath(Path().apply { moveTo(w * 0.16f, h * 0.48f); lineTo(w * 0.50f, h * 0.18f); lineTo(w * 0.84f, h * 0.48f); lineTo(w * 0.74f, h * 0.48f); lineTo(w * 0.74f, h * 0.82f); lineTo(w * 0.26f, h * 0.82f); lineTo(w * 0.26f, h * 0.48f); close() }, tint, style = stroke)
            LyraIconType.MUSIC -> {
                drawLine(tint, Offset(w * 0.38f, h * 0.70f), Offset(w * 0.38f, h * 0.24f), w * 0.09f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.74f, h * 0.64f), Offset(w * 0.74f, h * 0.18f), w * 0.09f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.74f, h * 0.18f), w * 0.11f, StrokeCap.Round)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.29f, h * 0.72f))
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.65f, h * 0.66f))
            }
            LyraIconType.LYRICS -> {
                drawRoundRect(tint, Offset(w * 0.36f, h * 0.16f), Size(w * 0.28f, h * 0.42f), CornerRadius(w * 0.14f), style = stroke)
                drawPath(Path().apply { moveTo(w * 0.24f, h * 0.44f); cubicTo(w * 0.24f, h * 0.72f, w * 0.76f, h * 0.72f, w * 0.76f, h * 0.44f) }, tint, style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.68f), Offset(w * 0.50f, h * 0.84f), w * 0.09f, StrokeCap.Round)
            }
            LyraIconType.SETTINGS -> {
                drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.14f), Offset(w * 0.50f, h * 0.24f), w * 0.10f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.76f), Offset(w * 0.50f, h * 0.86f), w * 0.10f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.14f, h * 0.50f), Offset(w * 0.24f, h * 0.50f), w * 0.10f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.76f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), w * 0.10f, StrokeCap.Round)
            }
            LyraIconType.SEARCH -> {
                drawCircle(tint, radius = w * 0.24f, center = Offset(w * 0.44f, h * 0.44f), style = stroke)
                drawLine(tint, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.82f, h * 0.82f), w * 0.10f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun LyraBrandLogo(size: Dp = 42.dp, cornerRadius: Dp = 12.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size).clip(RoundedCornerShape(cornerRadius))) {
        val w = this.size.width; val h = this.size.height
        drawRect(brush = Brush.linearGradient(listOf(Color(0xFF1E2B14), Color(0xFF0C1009)), Offset.Zero, Offset(w, h)))
        drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF8FE03C).copy(alpha = 0.25f), Color.Transparent), Offset(w * 0.5f, h * 0.5f), w * 0.45f), radius = w * 0.45f, center = Offset(w * 0.5f, h * 0.5f))
        val lyraPath = Path().apply { moveTo(w * 0.28f, h * 0.26f); cubicTo(w * 0.18f, h * 0.45f, w * 0.24f, h * 0.70f, w * 0.40f, h * 0.78f); lineTo(w * 0.60f, h * 0.78f); cubicTo(w * 0.76f, h * 0.70f, w * 0.82f, h * 0.45f, w * 0.72f, h * 0.26f) }
        drawPath(lyraPath, Brush.verticalGradient(listOf(Color(0xFFD9FF9A), Color(0xFF76D229))), style = Stroke(w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(Color(0xFFB8F36B), Offset(w * 0.34f, h * 0.86f), Offset(w * 0.66f, h * 0.86f), w * 0.055f, StrokeCap.Round)
        drawLine(Color(0xFF8FE03C), Offset(w * 0.39f, h * 0.40f), Offset(w * 0.39f, h * 0.66f), w * 0.05f, StrokeCap.Round)
        drawLine(Color(0xFFD9FF9A), Offset(w * 0.50f, h * 0.22f), Offset(w * 0.50f, h * 0.68f), w * 0.055f, StrokeCap.Round)
        drawLine(Color(0xFF8FE03C), Offset(w * 0.61f, h * 0.34f), Offset(w * 0.61f, h * 0.66f), w * 0.05f, StrokeCap.Round)
    }
}

@Composable
private fun SummaryCard(title: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedCard(modifier = modifier.clickable { onClick() }, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LyraBrandLogo(size = 54.dp, cornerRadius = 16.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TrackList(
    tracks: List<AudioTrack>,
    currentUri: Uri?,
    playing: Boolean,
    favoriteIds: Set<String>,
    playTracks: (List<AudioTrack>, Int) -> Unit,
    toggleFavorite: (AudioTrack) -> Unit,
    onAddToPlaylist: (AudioTrack) -> Unit,
    onEditTrack: (AudioTrack) -> Unit,
    showFolder: Boolean,
    modifier: Modifier = Modifier,
    scope: kotlinx.coroutines.CoroutineScope? = null,
    context: Context? = null,
    onStatusChange: ((String) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        EmptyState("Tidak ada lagu yang cocok", "Ubah kata kunci pencarian atau pindai ulang.")
        return
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(tracks, key = { it.id }) { song ->
            val isCurrent = song.uri == currentUri
            val isFav = song.id in favoriteIds
            val isOnlineYtm = song.uri.toString().startsWith("ytm://") // Deteksi apakah ini lagu YouTube Music

            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else Color.Transparent)
                    .clickable { playTracks(tracks, tracks.indexOfFirst { it.id == song.id }) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp)) {
                    if (song.artworkUri != null) AsyncImage(model = song.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Box(contentAlignment = Alignment.Center) { if (isCurrent && playing) LyraBrandLogo(size = 48.dp, cornerRadius = 12.dp) else LyraVectorIcon(LyraIconType.MUSIC, tint = MaterialTheme.colorScheme.secondary, size = 22.dp) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (song.duration > 0L) Text(formatTime(song.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (isOnlineYtm) "YouTube Music Online" else "${song.artist} · ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // TOMBOL DOWNLOAD KHUSUS LAGU ONLINE YTM
                if (isOnlineYtm && scope != null && context != null) {
                    IconButton(
                        onClick = {
                            val videoId = song.uri.host ?: song.uri.lastPathSegment.orEmpty()
                            scope.launch(Dispatchers.IO) {
                                val success = YtmClient.downloadSongToDevice(
                                    context,
                                    YtmTrackItem(videoId, song.title, song.artist, song.album, song.duration, "", song.artworkUri?.toString().orEmpty())
                                )
                                withContext(Dispatchers.Main) {
                                    onStatusChange?.invoke(if (success) "Unduhan berhasil disimpan ke folder Music!" else "Gagal mengunduh lagu.")
                                }
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Text("↓", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                IconButton(onClick = { toggleFavorite(song) }, modifier = Modifier.size(34.dp)) { LyraVectorIcon(if (isFav) LyraIconType.HEART_FILLED else LyraIconType.HEART_OUTLINE, tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) }
                IconButton(onClick = { onAddToPlaylist(song) }, modifier = Modifier.size(34.dp)) { LyraVectorIcon(LyraIconType.PLUS, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        }
    }
}
@Composable
private fun KaraokeWordText(line: KaraokeLine, positionMs: Long, isCurrentLine: Boolean, fontSizeSp: Int, accentColor: Color, textAlign: TextAlign = TextAlign.Center, modifier: Modifier = Modifier) {
    val sungColor = Color.White
    val unsungColor = if (isCurrentLine) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.25f)
    Column(modifier = modifier, horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        if (!isCurrentLine || line.words.isEmpty()) {
            Text(line.plainText.ifBlank { "•••" }, fontSize = fontSizeSp.sp, fontWeight = if (isCurrentLine) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (isCurrentLine) accentColor else unsungColor, textAlign = textAlign, lineHeight = (fontSizeSp + 7).sp)
        } else {
            val annotated = buildAnnotatedString {
                for (word in line.words) {
                    when {
                        positionMs >= word.endMs -> withStyle(SpanStyle(color = sungColor, fontWeight = FontWeight.ExtraBold)) { append(word.text) }
                        positionMs in word.startMs until word.endMs -> {
                            val dur = (word.endMs - word.startMs).coerceAtLeast(1L)
                            val splitIdx = (word.text.length * ((positionMs - word.startMs).toFloat() / dur.toFloat()).coerceIn(0f, 1f)).toInt().coerceIn(0, word.text.length)
                            withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Black)) { append(word.text.take(splitIdx)) }
                            withStyle(SpanStyle(color = accentColor.copy(alpha = 0.65f), fontWeight = FontWeight.ExtraBold)) { append(word.text.drop(splitIdx)) }
                        }
                        else -> withStyle(SpanStyle(color = unsungColor, fontWeight = FontWeight.Bold)) { append(word.text) }
                    }
                }
            }
            Text(annotated, fontSize = fontSizeSp.sp, textAlign = textAlign, lineHeight = (fontSizeSp + 7).sp)
        }
        if (line.translationText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = line.translationText,
                fontSize = (fontSizeSp - 4).coerceAtLeast(12).sp,
                fontStyle = FontStyle.Italic,
                color = if (isCurrentLine) accentColor.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                textAlign = textAlign
            )
        }
    }
}

@Composable
private fun KaraokeMiniStage(lines: List<KaraokeLine>, positionMs: Long, hasEnhancedTag: Boolean, autoWordKaraoke: Boolean, accentColor: Color, onExpandClick: () -> Unit, onOpenSearchClick: () -> Unit) {
    val activeIdx = remember(lines, positionMs) { lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0) }
    val currentLine = lines.getOrNull(activeIdx); val nextLine = lines.getOrNull(activeIdx + 1)
    ElevatedCard(shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.fillMaxWidth().clickable { if (lines.isEmpty()) onOpenSearchClick() else onExpandClick() }.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(when { hasEnhancedTag -> "KARAOKE ENHANCED LRC ★"; autoWordKaraoke -> "AUTO-WORD KARAOKE"; else -> "SYNCED LRC (PER BARIS)" }, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Black)
                Text(if (lines.isEmpty()) "Cari di Lyricsify / LRCLIB ⌕" else "Layar Penuh ↗", style = MaterialTheme.typography.labelSmall, color = accentColor)
            }
            Spacer(Modifier.height(4.dp))
            if (currentLine == null) Text("Lirik belum tersedia — ketuk untuk mencari di Lyricsify & LRCLIB", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
            else {
                KaraokeWordText(currentLine, positionMs, true, 17, accentColor, TextAlign.Center, Modifier.fillMaxWidth())
                if (nextLine != null) { Spacer(Modifier.height(3.dp)); Text(nextLine.plainText.ifBlank { "•••" }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
// Di dalam composable LyraApp atau di atas KaraokeFullStage, kita bisa cek orientasi:

private fun KaraokeFullStage(lines: List<KaraokeLine>, positionMs: Long, hasEnhancedTag: Boolean, autoWordKaraoke: Boolean, accentColor: Color, darkBgColor: Color, onToggleAutoWord: () -> Unit, onShareClick: () -> Unit, onSeekTo: (Long) -> Unit, onEditLyricsClick: () -> Unit, modifier: Modifier = Modifier) {
    KeepScreenOnEffect(enabled = true)
    val listState = rememberLazyListState()
    val activeIdx = remember(lines, positionMs) { lines.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0) }
    LaunchedEffect(activeIdx, lines.size) { if (lines.isNotEmpty() && activeIdx in lines.indices) listState.animateScrollToItem(index = activeIdx, scrollOffset = -170) }
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(darkBgColor, Color(0xFF12170E), Color(0xFF0D0F0B)))).border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(26.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (lines.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LyraBrandLogo(size = 64.dp, cornerRadius = 18.dp); Spacer(Modifier.height(12.dp))
                Text("Lirik Belum Tersedia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Mendukung pencarian otomatis Enhanced LRC dari Lyricsify.com & LRCLIB.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                Button(onClick = onEditLyricsClick, shape = RoundedCornerShape(16.dp)) { Text("⌕ Cari di Lyricsify & LRCLIB") }
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 85.dp), verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(lines) { index, line ->
                    val isCurrent = index == activeIdx; val distance = abs(index - activeIdx)
                    val scale by animateFloatAsState(if (isCurrent) 1.03f else 0.95f, tween(220), label = "kS")
                    val alphaVal by animateFloatAsState(when { isCurrent -> 1f; distance == 1 -> 0.60f; distance == 2 -> 0.36f; else -> 0.20f }, tween(220), label = "kA")
                    Column(Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.alpha(alphaVal).clip(RoundedCornerShape(18.dp)).background(if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent).clickable { onSeekTo(line.startMs) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(formatTime(line.startMs), style = MaterialTheme.typography.labelSmall, color = if (isCurrent) accentColor else Color.White.copy(alpha = 0.35f))
                            if (isCurrent) Text(if (line.isEnhanced) "★ ENHANCED KARAOKE" else "● AKTIF", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = accentColor, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(4.dp))
                        KaraokeWordText(line, positionMs, isCurrent, if (isCurrent) 21 else 16, accentColor, TextAlign.Start, Modifier.fillMaxWidth())
                        if (isCurrent && line.words.isNotEmpty()) {
                            val lineProg = ((positionMs - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { lineProg }, modifier = Modifier.fillMaxWidth(0.6f).padding(top = 8.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = accentColor, trackColor = Color.White.copy(alpha = 0.15f))
                        }
                    }
                }
            }
            Row(modifier = Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { onShareClick() }) {
                    Text("Bagikan Kutipan ↗", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { onToggleAutoWord() }) {
                    Text(when { hasEnhancedTag -> "ENHANCED LRC ★"; autoWordKaraoke -> "AUTO-WORD: ON"; else -> "AUTO-WORD: OFF" }, style = MaterialTheme.typography.labelSmall, color = if (hasEnhancedTag || autoWordKaraoke) accentColor else Color.LightGray, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun EqualizerPanel(audioSessionId: Int) {
    var enabled by remember(audioSessionId) { mutableStateOf(false) }
    var bandValues by remember(audioSessionId) { mutableStateOf<List<Float>>(listOf(0f, 0f, 0f, 0f, 0f)) }
    var range by remember(audioSessionId) { mutableFloatStateOf(1000f) }
    var labels by remember(audioSessionId) { mutableStateOf<List<String>>(listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")) }
    var bass by remember(audioSessionId) { mutableFloatStateOf(0f) }
    var virtualizerStrength by remember(audioSessionId) { mutableFloatStateOf(0f) }
    var loudnessGainMb by remember(audioSessionId) { mutableFloatStateOf(0f) }
    var selectedReverb by remember(audioSessionId) { mutableIntStateOf(0) }

    val eq = remember(audioSessionId) { if (audioSessionId > 0) runCatching { Equalizer(0, audioSessionId) }.getOrNull() else null }
    val bassBoost = remember(audioSessionId) { if (audioSessionId > 0) runCatching { BassBoost(0, audioSessionId) }.getOrNull() else null }
    val virtualizer = remember(audioSessionId) { if (audioSessionId > 0) runCatching { Virtualizer(0, audioSessionId) }.getOrNull() else null }
    val loudness = remember(audioSessionId) { if (audioSessionId > 0) runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull() else null }
    val reverb = remember(audioSessionId) { if (audioSessionId > 0) runCatching { PresetReverb(0, audioSessionId) }.getOrNull() else null }

    DisposableEffect(eq, bassBoost, virtualizer, loudness, reverb) {
        onDispose {
            runCatching { eq?.release() }; runCatching { bassBoost?.release() }
            runCatching { virtualizer?.release() }; runCatching { loudness?.release() }; runCatching { reverb?.release() }
        }
    }
    LaunchedEffect(eq) {
        if (eq != null) runCatching {
            val r = eq.bandLevelRange; range = maxOf(abs(r[0].toFloat()), abs(r[1].toFloat()))
            val count = eq.numberOfBands.toInt().coerceAtMost(10)
            bandValues = (0 until count).map { eq.getBandLevel(it.toShort()).toFloat() }
            labels = (0 until count).map { b -> val hz = eq.getCenterFreq(b.toShort()) / 1000; if (hz >= 1000) "${hz / 1000f} kHz" else "$hz Hz" }
            enabled = eq.enabled
        }
    }
    if (audioSessionId <= 0 || eq == null) { Text("Mulai putar lagu untuk mengaktifkan studio equalizer & efek 3D.", style = MaterialTheme.typography.bodySmall); return }
    Row(verticalAlignment = Alignment.CenterVertically) { Text("Equalizer & 3D Studio", Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Switch(checked = enabled, onCheckedChange = { v -> enabled = v; runCatching { eq.enabled = v } }) }
    val presets = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f),
        "Bass+" to listOf(0.7f, 0.4f, 0f, 0.1f, 0.2f),
        "Vocal/Karaoke" to listOf(-0.3f, 0.2f, 0.75f, 0.6f, 0.1f),
        "Pop" to listOf(0.3f, 0.5f, 0.2f, 0.4f, 0.4f),
        "Rock" to listOf(0.6f, 0.2f, -0.2f, 0.4f, 0.7f)
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        presets.forEach { (name, ratios) ->
            AssistChip(
                onClick = {
                    enabled = true; runCatching { eq.enabled = true }
                    val updated = bandValues.indices.map { i -> (ratios.getOrElse(i) { 0f } * range).coerceIn(-range, range) }
                    bandValues = updated
                    updated.forEachIndexed { idx, v -> runCatching { eq.setBandLevel(idx.toShort(), v.toInt().toShort()) } }
                },
                label = { Text(name, fontSize = 11.sp) }
            )
        }
    }
    bandValues.forEachIndexed { idx, value ->
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(labels.getOrElse(idx) { "Band ${idx + 1}" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("${value.toInt()} mB", style = MaterialTheme.typography.labelSmall) }
            Slider(value = value, onValueChange = { next -> bandValues = bandValues.toMutableList().also { it[idx] = next }; runCatching { eq.setBandLevel(idx.toShort(), next.toInt().toShort()) } }, valueRange = -range..range, enabled = enabled)
        }
    }
    if (bassBoost != null) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Bass Boost", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("${bass.toInt()}%", style = MaterialTheme.typography.labelSmall) }
        Slider(value = bass, onValueChange = { next -> bass = next; runCatching { bassBoost.setStrength((next * 10).toInt().toShort()); bassBoost.enabled = next > 0f } }, valueRange = 0f..100f)
    }
    if (virtualizer != null) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("3D Surround Virtualizer", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("${virtualizerStrength.toInt()}%", style = MaterialTheme.typography.labelSmall) }
        Slider(value = virtualizerStrength, onValueChange = { next -> virtualizerStrength = next; runCatching { virtualizer.setStrength((next * 10).toInt().toShort()); virtualizer.enabled = next > 0f } }, valueRange = 0f..100f)
    }
    if (loudness != null) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Vocal & Clarity Gain", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("+${(loudnessGainMb / 100f).toInt()} dB", style = MaterialTheme.typography.labelSmall) }
        Slider(value = loudnessGainMb, onValueChange = { next -> loudnessGainMb = next; runCatching { loudness.setTargetGain(next.toInt()); loudness.enabled = next > 0f } }, valueRange = 0f..1500f)
    }
    if (reverb != null) {
        Text("Hall Reverb (Efek Ruangan Karaoke)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        val reverbPresets = listOf("Off" to PresetReverb.PRESET_NONE.toInt(), "Small Room" to PresetReverb.PRESET_SMALLROOM.toInt(), "Medium Hall" to PresetReverb.PRESET_MEDIUMHALL.toInt(), "Large Hall" to PresetReverb.PRESET_LARGEHALL.toInt(), "Plate" to PresetReverb.PRESET_PLATE.toInt())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            reverbPresets.forEach { (label, code) ->
                FilterChip(
                    selected = selectedReverb == code,
                    onClick = { selectedReverb = code; runCatching { reverb.preset = code.toShort(); reverb.enabled = code != PresetReverb.PRESET_NONE.toInt() } },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }
    TextButton(onClick = {
        bandValues = List(bandValues.size) { 0f }; bandValues.forEachIndexed { i, _ -> runCatching { eq.setBandLevel(i.toShort(), 0.toShort()) } }
        bass = 0f; virtualizerStrength = 0f; loudnessGainMb = 0f; selectedReverb = 0
        runCatching { bassBoost?.setStrength(0.toShort()); bassBoost?.let { it.enabled = false } }
        runCatching { virtualizer?.setStrength(0.toShort()); virtualizer?.let { it.enabled = false } }
        runCatching { loudness?.setTargetGain(0); loudness?.let { it.enabled = false } }
        runCatching { reverb?.let { it.enabled = false } }
    }) { Text("Reset semua efek audio") }
}

@Composable
private fun AudioVisualizer(
    audioSessionId: Int,
    isPlaying: Boolean,
    style: VisualizerStyle,
    accentColor: Color,
    onCycleStyle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val binCount = 48
    var spectrum by remember { mutableStateOf<List<Float>>(List(binCount) { 0.06f }) }
    var captureError by remember { mutableStateOf(false) }
    var hasActiveSignal by remember { mutableStateOf(false) }

    DisposableEffect(audioSessionId, isPlaying, hasPermission) {
        var visualizer: Visualizer? = null
        val mainHandler = Handler(Looper.getMainLooper())
        if (isPlaying && hasPermission) {
            try {
                val capture = try { if (audioSessionId > 0) Visualizer(audioSessionId) else Visualizer(0) } catch (_: Exception) { Visualizer(0) }
                capture.enabled = false
                val range = Visualizer.getCaptureSizeRange()
                capture.captureSize = range[1].coerceIn(minOf(256, range[0]), 1024)
                capture.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform == null || waveform.isEmpty() || hasActiveSignal) return
                        val values = MutableList(binCount) { 0.05f }
                        val step = (waveform.size / binCount).coerceAtLeast(1)
                        var maxAmp = 0f
                        for (i in 0 until binCount) {
                            val amp = abs((waveform[(i * step).coerceIn(0, waveform.size - 1)].toInt() and 0xFF) - 128) / 128f
                            if (amp > maxAmp) maxAmp = amp; values[i] = amp.coerceIn(0.05f, 1f)
                        }
                        if (maxAmp > 0.02f) mainHandler.post { spectrum = spectrum.mapIndexed { idx, old -> (old * 0.35f + values[idx] * 0.65f).coerceIn(0.05f, 1f) } }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || fft.size < 8) return
                        val values = MutableList(binCount) { 0.04f }; val maxBin = (fft.size / 2).coerceAtLeast(2); var maxPeak = 0.0
                        for (i in 0 until binCount) {
                            val low = (1.0 * maxBin.toDouble().pow(i.toDouble() / binCount)).toInt().coerceIn(1, maxBin - 1)
                            val high = (1.0 * maxBin.toDouble().pow((i + 1).toDouble() / binCount)).toInt().coerceIn(low + 1, maxBin)
                            var peak = 0.0
                            for (bin in low until high) { val r = fft[2 * bin].toInt(); val im = fft[2 * bin + 1].toInt(); val mag = sqrt((r * r + im * im).toDouble()); if (mag > peak) peak = mag }
                            if (peak > maxPeak) maxPeak = peak
                            values[i] = (ln(1.0 + peak) / ln(1.0 + 150.0)).toFloat().coerceIn(0.04f, 1f)
                        }
                        hasActiveSignal = maxPeak > 1.5
                        if (hasActiveSignal) mainHandler.post { spectrum = spectrum.mapIndexed { idx, old -> (old * 0.28f + values[idx] * 0.72f).coerceIn(0.04f, 1f) } }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                capture.enabled = true; visualizer = capture; captureError = false
            } catch (_: Exception) { captureError = true; runCatching { visualizer?.release() }; visualizer = null }
        } else { spectrum = List(binCount) { 0.04f }; captureError = false; hasActiveSignal = false }
        onDispose { runCatching { visualizer?.enabled = false }; runCatching { visualizer?.release() }; mainHandler.removeCallbacksAndMessages(null) }
    }

    val infinite = rememberInfiniteTransition(label = "vis")
    val phase by infinite.animateFloat(0f, (2.0 * Math.PI).toFloat(), infiniteRepeatable(tween(850, easing = LinearEasing)), label = "ph")
    val useFallback = !hasPermission || captureError || !hasActiveSignal
    val renderSpectrum = if (useFallback && isPlaying) List(binCount) { i -> (0.08f + 0.78f * abs(sin(phase + i * 0.42f)) * (0.35f + 0.65f * abs(sin(phase * 0.63f + i * 0.13f)))).coerceIn(0.05f, 0.95f) }
    else if (!isPlaying) List(binCount) { i -> 0.04f + 0.02f * abs(sin(i * 0.3f)) } else spectrum

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "SPECTRUM: ${style.label.uppercase()} ↻",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onCycleStyle() }
            )
            if (!hasPermission) Text("Izinkan mikrofon (FFT) →", style = MaterialTheme.typography.labelSmall, color = accentColor, modifier = Modifier.clickable { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) })
            else Text(when { !isPlaying -> "STANDBY"; hasActiveSignal && !captureError -> "REAL-TIME FFT"; else -> "PULSE ENGINE" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(48.dp).clickable { onCycleStyle() }) {
            val count = renderSpectrum.size
            val gap = 2.4f
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1.5f)
            when (style) {
                VisualizerStyle.NEON_BARS -> {
                    for (i in 0 until count) {
                        val h = size.height * renderSpectrum[i].coerceIn(0.04f, 1f)
                        drawRoundRect(Brush.verticalGradient(listOf(Color.White, accentColor, accentColor.copy(alpha = 0.25f))), Offset(i * (barWidth + gap), size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2f, barWidth / 2f))
                    }
                }
                VisualizerStyle.MIRROR_WAVE -> {
                    val centerY = size.height / 2f
                    for (i in 0 until count) {
                        val h = (size.height * renderSpectrum[i].coerceIn(0.06f, 1f)).coerceAtLeast(4f)
                        drawRoundRect(Brush.verticalGradient(listOf(accentColor, Color.White, accentColor)), Offset(i * (barWidth + gap), centerY - h / 2f), Size(barWidth, h), CornerRadius(barWidth / 2f, barWidth / 2f))
                    }
                }
                VisualizerStyle.CYBER_DOTS -> {
                    for (i in 0 until count) {
                        val frac = renderSpectrum[i].coerceIn(0.05f, 1f)
                        val x = i * (barWidth + gap) + barWidth / 2f
                        val y = size.height - (size.height * frac)
                        drawLine(accentColor.copy(alpha = 0.35f), Offset(x, size.height), Offset(x, y), strokeWidth = barWidth * 0.45f)
                        drawCircle(accentColor, radius = barWidth * 0.65f, center = Offset(x, y.coerceIn(4f, size.height - 4f)))
                    }
                }
                VisualizerStyle.PULSE_LINE -> {
                    val path = Path()
                    for (i in 0 until count) {
                        val x = i * (barWidth + gap) + barWidth / 2f
                        val y = size.height - (size.height * renderSpectrum[i].coerceIn(0.05f, 0.95f))
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, Brush.horizontalGradient(listOf(accentColor, Color.White, accentColor)), style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

// === 4. MAIN ACTIVITY & ROOT APP ===
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyraApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("lyra", Context.MODE_PRIVATE) }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    var songs by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var metadataOverrides by remember { mutableStateOf<Map<String, MetadataOverride>>(loadMetadataOverrides(prefs.getString("metadata_overrides_json", "{}").orEmpty())) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Siap memindai koleksi musik.") }

    var tab by remember { mutableStateOf(LyraTab.HOME) }
    var librarySubTab by remember { mutableStateOf(LibrarySubTab.ALL) }
    var selectedBrowseFolder by remember { mutableStateOf<String?>(null) }
    var lyricsSubTab by remember { mutableStateOf(LyricsSubTab.PREVIEW) }
    var isSearchingLyrics by remember { mutableStateOf(false) }

    var minDurationSec by remember { mutableIntStateOf(prefs.getInt("min_duration_sec", 60)) }
    var sortOption by remember { mutableStateOf(runCatching { SortOption.valueOf(prefs.getString("sort_option", SortOption.DATE_ADDED_DESC.name).orEmpty()) }.getOrDefault(SortOption.DATE_ADDED_DESC)) }
    var visualizerStyle by remember { mutableStateOf(runCatching { VisualizerStyle.valueOf(prefs.getString("visualizer_style", VisualizerStyle.NEON_BARS.name).orEmpty()) }.getOrDefault(VisualizerStyle.NEON_BARS)) }
    var themePreset by remember { mutableStateOf(runCatching { ThemeAccent.valueOf(prefs.getString("theme_preset", ThemeAccent.LYRA_NEON.name).orEmpty()) }.getOrDefault(ThemeAccent.LYRA_NEON)) }
    var vinylMode by remember { mutableStateOf(prefs.getBoolean("vinyl_mode", true)) }
    var smoothFadePause by remember { mutableStateOf(prefs.getBoolean("smooth_fade", true)) }
    var autoWordKaraoke by remember { mutableStateOf(prefs.getBoolean("auto_word_karaoke", true)) }
    var bluetoothLiveLyrics by remember { mutableStateOf(prefs.getBoolean("bt_live_lyrics", false)) }

    var isRecordingKaraoke by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var activeRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var lastRecordedPath by remember { mutableStateOf("") }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var pitchSemitones by remember { mutableIntStateOf(0) }
    var showAudioLabDialog by remember { mutableStateOf(false) }
    var showShareQuoteDialog by remember { mutableStateOf(false) }
    var loopStartMs by remember { mutableStateOf<Long?>(null) }
    var loopEndMs by remember { mutableStateOf<Long?>(null) }

    var playing by remember { mutableStateOf(false) }
    var audioSessionId by remember { mutableIntStateOf(prefs.getInt("audio_session_id", 0)) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTitle by remember { mutableStateOf(prefs.getString("current_title", "Belum ada lagu").orEmpty()) }
    var currentArtist by remember { mutableStateOf(prefs.getString("current_artist", "").orEmpty()) }
    var currentAlbum by remember { mutableStateOf(prefs.getString("current_album", "").orEmpty()) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var playlists by remember { mutableStateOf<List<LyraPlaylist>>(loadPlaylists(prefs.getString("playlists_json", "[]").orEmpty())) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var trackToAdd by remember { mutableStateOf<AudioTrack?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var selectedFolder by remember { mutableStateOf(prefs.getString("music_folder_uri", "").orEmpty()) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }
    var searchTrack by remember { mutableStateOf("") }
    var searchArtist by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LrcResult>>(emptyList()) }
    var lrcText by remember { mutableStateOf(prefs.getString("current_lrc", "").orEmpty()) }
    var currentLyric by remember { mutableStateOf("") }
    var lyricsStatus by remember { mutableStateOf("Lirik otomatis siap (Lyricsify + LRCLIB)") }
    var autoLyrics by remember { mutableStateOf(prefs.getBoolean("auto_lyrics", true)) }
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var smartShuffle by remember { mutableStateOf(prefs.getBoolean("smart_shuffle", true)) }
    var lastPlayed by remember { mutableStateOf<List<String>>(prefs.getString("recent_ids", "").orEmpty().split("|").filter { it.isNotBlank() }) }
    var playCounts by remember { mutableStateOf<Map<String, Int>>(loadCounts(prefs.getString("play_counts_json", "{}").orEmpty())) }

    var showMetadataDialog by remember { mutableStateOf(false) }
    var editingTrackTarget by remember { mutableStateOf<AudioTrack?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editArtist by remember { mutableStateOf("") }
    var editAlbum by remember { mutableStateOf("") }
    var editArtworkUri by remember { mutableStateOf("") }
    var showFullKaraokeInPlayer by remember { mutableStateOf(false) }
    var ytmQuery by remember { mutableStateOf("") }
    var ytmResults by remember { mutableStateOf<List<YtmTrackItem>>(emptyList()) }
    var isSearchingYtm by remember { mutableStateOf(false) }
    var ytmStatus by remember { mutableStateOf("Cari & streaming lagu langsung dari YouTube Music") }

    fun ytmItemToAudioTrack(item: YtmTrackItem): AudioTrack {
        return AudioTrack(
            id = "ytm:${item.videoId}",
            uri = Uri.parse("ytm://${item.videoId}"),
            title = item.title,
            artist = item.artist,
            album = item.album,
            duration = item.durationMs,
            folder = "YouTube Music Stream",
            artworkUri = item.thumbnailUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        )
    }

    fun triggerYtmSearch(query: String) {
        val q = query.trim()
        if (q.isBlank()) return
        scope.launch {
            isSearchingYtm = true
            ytmStatus = "Mencari \"$q\" di YouTube Music..."
            try {
                val found = withContext(Dispatchers.IO) { YtmClient.searchSongs(q) }
                ytmResults = found
                ytmStatus = if (found.isEmpty()) "Tidak ada hasil untuk \"$q\"." else "Ditemukan ${found.size} lagu siap streaming."
            } catch (e: Exception) {
                ytmStatus = "Gagal terhubung ke YouTube Music: ${e.message}"
            } finally {
                isSearchingYtm = false
            }
        }
    }

    fun loadYtmRadio(seedVideoId: String, seedTitle: String) {
        scope.launch {
            isSearchingYtm = true
            ytmStatus = "Memuat radio otomatis untuk \"$seedTitle\"..."
            try {
                val radio = withContext(Dispatchers.IO) { YtmClient.fetchRadioTracks(seedVideoId) }
                if (radio.isNotEmpty()) {
                    ytmResults = radio
                    ytmStatus = "Radio \"$seedTitle\" (${radio.size} lagu)"
                }
            } catch (_: Exception) {
            } finally {
                isSearchingYtm = false
            }
        }
    }

    
    val parsedKaraokeLines = remember(lrcText, autoWordKaraoke) { EnhancedLrcEngine.parse(lrcText, autoWordKaraoke) }
    val hasEnhancedKaraoke = remember(parsedKaraokeLines) { parsedKaraokeLines.any { it.isEnhanced } }

    fun permissionName() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) status = "Izin mikrofon diperlukan untuk merekam karaoke."
    }

    fun toggleKaraokeRecording() {
        if (isRecordingKaraoke) {
            runCatching { activeRecorder?.stop(); activeRecorder?.release() }
            activeRecorder = null
            isRecordingKaraoke = false
            status = "Rekaman Karaoke tersimpan: $lastRecordedPath"
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            runCatching {
                val outDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val cleanName = currentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(25).ifBlank { "Lyra" }
                val outFile = File(outDir, "Karaoke_${cleanName}_${System.currentTimeMillis() / 1000}.m4a")
                val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128000)
                rec.setAudioSamplingRate(44100)
                rec.setOutputFile(outFile.absolutePath)
                rec.prepare()
                rec.start()
                activeRecorder = rec
                lastRecordedPath = outFile.name
                recordingSeconds = 0
                isRecordingKaraoke = true
                status = "Merekam suara karaoke..."
            }.onFailure { status = "Gagal memulai perekam: ${it.message}" }
        }
    }

    LaunchedEffect(isRecordingKaraoke) {
        while (isRecordingKaraoke) {
            delay(1000)
            recordingSeconds += 1
        }
    }

    fun togglePlayWithFade() {
        val c = controller ?: return
        if (playing) {
            if (smoothFadePause) {
                scope.launch {
                    for (step in 4 downTo 1) { runCatching { c.volume = step * 0.2f }; delay(45) }
                    c.pause()
                    runCatching { c.volume = 1.0f }
                }
            } else c.pause()
        } else {
            runCatching { c.volume = 1.0f }
            c.play()
        }
    }

    fun applyPlaybackParams(speed: Float, semitones: Int) {
        val pitchFactor = 2.0.pow(semitones / 12.0).toFloat().coerceIn(0.5f, 2.0f)
        runCatching { controller?.playbackParameters = PlaybackParameters(speed.coerceIn(0.5f, 2.0f), pitchFactor) }
    }

    fun saveLrcToCache(lyrics: String, title: String = currentTitle, artist: String = currentArtist) {
        val key = lyricsCacheKey(title, artist)
        val cache = runCatching { JSONObject(prefs.getString("lyrics_cache_json", "{}").orEmpty()) }.getOrElse { JSONObject() }
        cache.put(key, lyrics)
        while (cache.length() > 180) { val fk = cache.keys().asSequence().firstOrNull() ?: break; cache.remove(fk) }
        prefs.edit().putString("current_lrc", lyrics).putString("current_lrc_key", key).putString("lyrics_cache_json", cache.toString()).apply()
    }

    fun triggerManualLyricsSearch(queryTitle: String, queryArtist: String) {
        if (queryTitle.isBlank()) return
        scope.launch {
            isSearchingLyrics = true
            val (cTitle, cArtist) = cleanSearchQuery(queryTitle, queryArtist)
            lyricsStatus = "Mencari di Lyricsify.com & LRCLIB: \"$cTitle\"..."
            try {
                val res = smartLyricsSearch(queryTitle, queryArtist)
                results = res
                val enhCount = res.count { LyricsifyExtractor.hasEnhancedKaraokeTags(it.syncedLyrics) }
                lyricsStatus = when {
                    res.isEmpty() -> "Tidak ditemukan hasil untuk \"$cTitle\". Coba tempel link URL Lyricsify."
                    enhCount > 0 -> "Ditemukan ${res.size} lirik ($enhCount Enhanced Karaoke ★ diprioritaskan)."
                    else -> "Ditemukan ${res.size} lirik dari Lyricsify & LRCLIB."
                }
            } catch (e: Exception) { lyricsStatus = "Gagal mencari lirik: ${e.message ?: "periksa internet"}" }
            finally { isSearchingLyrics = false }
        }
    }

    fun findOverrideFor(id: String, uriStr: String, filePath: String = "", overrides: Map<String, MetadataOverride> = metadataOverrides): MetadataOverride? {
        return overrides[id] ?: overrides[uriStr] ?: (if (filePath.isNotBlank()) overrides["path:$filePath"] else null)
    }

    fun applyTrackOverride(raw: AudioTrack, overrides: Map<String, MetadataOverride> = metadataOverrides): AudioTrack {
        val ov = findOverrideFor(raw.id, raw.uri.toString(), raw.filePath, overrides) ?: return raw
        val customArt = ov.artworkUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: raw.artworkUri
        return raw.copy(title = ov.title.ifBlank { raw.title }, artist = ov.artist.ifBlank { raw.artist }, album = ov.album.ifBlank { raw.album }, artworkUri = customArt)
    }

    fun openMetadataEditor(track: AudioTrack?) {
        editingTrackTarget = track
        editTitle = track?.title ?: currentTitle
        editArtist = track?.artist ?: currentArtist
        editAlbum = track?.album ?: currentAlbum
        editArtworkUri = track?.artworkUri?.toString().orEmpty()
        showMetadataDialog = true
    }

    fun scanSongs() {
        try {
            val latestOverrides = loadMetadataOverrides(prefs.getString("metadata_overrides_json", "{}").orEmpty())
            metadataOverrides = latestOverrides
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DATE_ADDED
            )
            val found = linkedMapOf<String, AudioTrack>()
            context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor: Cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol).toString()
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Judul tidak diketahui"
                    val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artis tidak diketahui"
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Album tidak diketahui"
                    val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                    val addedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val art = if (albumId > 0L) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null
                    val rawTrack = AudioTrack("media:$id", Uri.withAppendedPath(collection, id), title, artist, album, cursor.getLong(durationCol), addedSec, path, path.substringBeforeLast("/", ""), artworkUri = art)
                    found["media:$id"] = applyTrackOverride(rawTrack, latestOverrides)
                }
            }
            if (selectedFolder.isNotBlank()) {
                runCatching {
                    val tree = DocumentFile.fromTreeUri(context, Uri.parse(selectedFolder))
                    fun visit(folder: DocumentFile, depth: Int) {
                        if (depth > 12) return
                        for (file in folder.listFiles()) {
                            if (file.isDirectory) visit(file, depth + 1)
                            else if (file.isFile && file.name.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "3gp", "mid", "amr")) {
                                val id = "saf:${file.uri}"
                                val title = file.name.orEmpty().substringBeforeLast('.').ifBlank { "Audio tanpa judul" }
                                val modifiedSec = file.lastModified() / 1000L
                                if (!found.containsKey(id)) {
                                    val rawSaf = AudioTrack(id, file.uri, title, "Artis tidak diketahui", "Folder pilihan", 0L, modifiedSec, file.uri.path.orEmpty(), file.parentFile?.name.orEmpty(), true)
                                    found[id] = applyTrackOverride(rawSaf, latestOverrides)
                                }
                            }
                        }
                    }
                    if (tree != null) visit(tree, 0)
                }
            }
            // Pastikan daftar lagu dibersihkan dari duplikat berdasarkan jalur file (filePath) atau ID
songs = found.values.distinctBy { if (it.filePath.isNotBlank()) it.filePath else it.id }.toList()
            status = if (songs.isEmpty()) "Belum ada lagu ditemukan." else "${songs.size} file audio siap diputar."
        } catch (e: Exception) { status = "Gagal membaca musik: ${e.message}" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scanSongs() else status = "Izin audio diperlukan." }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            selectedFolder = uri.toString(); prefs.edit().putString("music_folder_uri", selectedFolder).apply(); scanSongs()
        }
    }
    val artworkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }; editArtworkUri = uri.toString() }
    }
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            lrcText = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            saveLrcToCache(lrcText); lyricsStatus = "Berkas LRC lokal dimuat."; lyricsSubTab = LyricsSubTab.PREVIEW
        } catch (e: Exception) { lyricsStatus = "Gagal membuka LRC: ${e.message}" }
    }
    val saveLrcLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(lrcText) }; lyricsStatus = "Berkas LRC diekspor." } catch (e: Exception) { lyricsStatus = "Gagal ekspor: ${e.message}" }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            val backup = JSONObject().put("format", "lyra-backup-v1").put("favorites", JSONArray(favoriteIds.toList())).put("recent_ids", JSONArray(lastPlayed)).put("play_counts", serializeCounts(playCounts)).put("playlists", serializePlaylists(playlists)).put("dark_mode", darkMode).put("smart_shuffle", smartShuffle)
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(backup.toString(2)) }; status = "Backup Lyra diekspor."
        } catch (e: Exception) { status = "Gagal ekspor backup: ${e.message}" }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val root = JSONObject(context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty())
            require(root.optString("format") == "lyra-backup-v1") { "Format tidak dikenal" }
            favoriteIds = root.optJSONArray("favorites").toStringList().toSet(); lastPlayed = root.optJSONArray("recent_ids").toStringList()
            playCounts = loadCounts(root.optJSONObject("play_counts")?.toString() ?: "{}"); playlists = loadPlaylists(root.optJSONArray("playlists")?.toString() ?: "[]")
            darkMode = root.optBoolean("dark_mode", true); smartShuffle = root.optBoolean("smart_shuffle", true)
            prefs.edit().putStringSet("favorites", favoriteIds).putString("recent_ids", lastPlayed.joinToString("|")).putString("play_counts_json", serializeCounts(playCounts).toString()).putString("playlists_json", serializePlaylists(playlists).toString()).putBoolean("dark_mode", darkMode).putBoolean("smart_shuffle", smartShuffle).apply()
            status = "Backup berhasil dipulihkan."
        } catch (e: Exception) { status = "Backup gagal: ${e.message}" }
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) try {
            val activeName = selectedPlaylist
            val listNow: List<AudioTrack> = when (activeName) { null -> songs; "Favorit" -> favoriteIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }; else -> playlists.firstOrNull { p -> p.name == activeName }?.trackIds?.mapNotNull { id -> songs.firstOrNull { s -> s.id == id } } ?: songs }
            val content = buildString { appendLine("#EXTM3U"); for (t in listNow) { appendLine("#EXTINF:${(t.duration / 1000).toInt()},${t.artist} - ${t.title}"); appendLine(t.uri.toString()) } }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }; status = "M3U diekspor."
        } catch (e: Exception) { status = "Gagal ekspor M3U: ${e.message}" }
    }
    val m3uImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()
            val resolved = lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { entry -> songs.firstOrNull { it.uri.toString() == entry.trim() } ?: songs.firstOrNull { it.title.equals(entry.substringAfterLast('/').substringBeforeLast('.'), true) } }.distinctBy { it.id }
            if (resolved.isNotEmpty()) {
                val name = "Impor M3U ${playlists.size + 1}"
                playlists = playlists + listOf(LyraPlaylist(name, resolved.map { it.id }))
                prefs.edit().putString("playlists_json", serializePlaylists(playlists).toString()).apply(); status = "Playlist $name dibuat."
            } else status = "Tidak ada lagu yang cocok."
        } catch (e: Exception) { status = "Gagal impor M3U: ${e.message}" }
    }

    fun savePlaylists() { prefs.edit().putString("playlists_json", serializePlaylists(playlists).toString()).apply() }
    fun toggleFavorite(track: AudioTrack) {
        favoriteIds = if (track.id in favoriteIds) favoriteIds - track.id else favoriteIds + track.id
        prefs.edit().putStringSet("favorites", favoriteIds).apply()
    }

 fun playTracks(list: List<AudioTrack>, index: Int) {
        val c = controller ?: run { 
            android.util.Log.e("LyraStream", "Error: MediaController (c) masih null!")
            status = "Pemutar sedang disiapkan."
            return 
        }
        if (list.isEmpty() || index !in list.indices) return
        
        val rawSong = list[index]
        val s = applyTrackOverride(rawSong)
        val uriStr = s.uri.toString()

        val isYtm = uriStr.startsWith("ytm://") || s.id.startsWith("ytm:")
        android.util.Log.d("LyraStream", "Mencoba memutar: ${s.title} | URI: $uriStr | isYtm: $isYtm")

        if (isYtm) {
            status = "Menghubungkan ke YouTube Music..."
            val videoId = when {
                uriStr.startsWith("ytm://") -> s.uri.host?.takeIf { it.isNotBlank() } ?: s.uri.lastPathSegment.orEmpty()
                else -> s.id.removePrefix("ytm:")
            }
            android.util.Log.d("LyraStream", "Extracted VideoId: $videoId")

            scope.launch(Dispatchers.IO) {
                try {
                    val realStreamUrl = YtmClient.resolveStreamUrl(videoId)
                    android.util.Log.d("LyraStream", "Hasil resolveStreamUrl: $realStreamUrl")
                    
                    withContext(Dispatchers.Main) {
                        if (realStreamUrl.isNullOrBlank()) {
                            android.util.Log.e("LyraStream", "Gagal: realStreamUrl kosong atau null!")
                            status = "Gagal memutar: URL stream kosong."
                            return@withContext
                        }

                        try {
                            val onlineUri = Uri.parse(realStreamUrl)
                            val mediaItem = MediaItem.Builder()
                                .setUri(onlineUri)
                                .setMediaId(s.id)
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(s.title)
                                        .setArtist(s.artist)
                                        .setAlbumTitle(s.album)
                                        .setArtworkUri(s.artworkUri)
                                        .build()
                                )
                                .build()

                            c.stop()
                            c.clearMediaItems()
                            c.setMediaItem(mediaItem)
                            c.prepare()
                            c.play()
                            android.util.Log.d("LyraStream", "Berhasil mengirim MediaItem ke ExoPlayer dan memanggil play()")
                            status = "Memutar: ${s.title}"
                        } catch (e: Exception) {
                            android.util.Log.e("LyraStream", "Exception saat setMediaItem/play: ${e.message}", e)
                            status = "Error pemutaran: ${e.message}"
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LyraStream", "Exception di IO scope saat resolveStreamUrl: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        status = "Error jaringan: ${e.message}"
                    }
                }
            }
        } else {
            // Pemutaran file lagu lokal perangkat
            c.stop()
            c.clearMediaItems()
            val items = list.map { songItem ->
                val track = applyTrackOverride(songItem)
                MediaItem.Builder()
                    .setUri(track.uri)
                    .setMediaId(track.id)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .setArtworkUri(track.artworkUri)
                            .build()
                    )
                    .build()
            }
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
            status = "Memutar: ${s.title}"
        }

        // Simpan status pemutaran terakhir
        currentTitle = s.title
        currentArtist = s.artist
        currentAlbum = s.album
        currentUri = s.uri
        loopStartMs = null
        loopEndMs = null

        val (cleanT, cleanA) = cleanSearchQuery(s.title, s.artist)
        searchTrack = cleanT
        searchArtist = cleanA

        val newRecent = (listOf(s.id) + lastPlayed.filterNot { it == s.id }).take(60)
        lastPlayed = newRecent
        val updatedCounts = playCounts.toMutableMap().also { it[s.id] = (it[s.id] ?: 0) + 1 }
        playCounts = updatedCounts

        prefs.edit()
            .putString("current_title", s.title)
            .putString("current_artist", s.artist)
            .putString("current_album", s.album)
            .putString("recent_ids", newRecent.joinToString("|"))
            .putString("play_counts_json", serializeCounts(updatedCounts).toString())
            .apply()
    }
   
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> = MediaController.Builder(context, token).buildAsync()
        future.addListener({ try { controller = future.get() } catch (_: Exception) {} }, context.mainExecutor)
        onDispose {
            runCatching { activeRecorder?.stop(); activeRecorder?.release() }
            MediaController.releaseFuture(future)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permissionName()) == PackageManager.PERMISSION_GRANTED) scanSongs()
        else permissionLauncher.launch(permissionName())
    }

    LaunchedEffect(controller, metadataOverrides, songs, loopStartMs, loopEndMs, bluetoothLiveLyrics) {
        val c = controller ?: return@LaunchedEffect
        var lastWidgetSync = 0L
        var lastBtLyricSent = ""
        while (true) {
            playing = c.isPlaying; audioSessionId = prefs.getInt("audio_session_id", 0)
            position = c.currentPosition.coerceAtLeast(0L)
            duration = c.duration.takeIf { it > 0 && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            shuffle = c.shuffleModeEnabled; repeatMode = c.repeatMode

            val lStart = loopStartMs; val lEnd = loopEndMs
            if (lStart != null && lEnd != null && lEnd > lStart + 500L && position >= lEnd) {
                c.seekTo(lStart); position = lStart
            }

            val activeUri = c.currentMediaItem?.localConfiguration?.uri; currentUri = activeUri
            val activeMediaId = c.currentMediaItem?.mediaId.orEmpty()
            val matchedSong = songs.firstOrNull { it.id == activeMediaId || it.uri == activeUri }
            val ov = findOverrideFor(activeMediaId, activeUri?.toString().orEmpty(), matchedSong?.filePath.orEmpty())

            currentTitle = ov?.title?.takeIf { it.isNotBlank() } ?: matchedSong?.title?.takeIf { it.isNotBlank() } ?: c.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() && !it.startsWith("♪ ") } ?: currentTitle
            currentArtist = ov?.artist?.takeIf { it.isNotBlank() } ?: matchedSong?.artist?.takeIf { it.isNotBlank() } ?: c.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() } ?: currentArtist
            currentAlbum = ov?.album?.takeIf { it.isNotBlank() } ?: matchedSong?.album?.takeIf { it.isNotBlank() } ?: c.mediaMetadata.albumTitle?.toString() ?: currentAlbum

            currentLyric = EnhancedLrcEngine.stripEnhancedTags(LrcParser.displayLine(lrcText, position))
            val now = System.currentTimeMillis()
            if (now - lastWidgetSync > 450L) {
                lastWidgetSync = now
                LyricsWidgetProvider.updateAll(context, currentTitle, currentLyric)
                if (bluetoothLiveLyrics && currentLyric.isNotBlank() && currentLyric != lastBtLyricSent) {
                    lastBtLyricSent = currentLyric
                    val idx = c.currentMediaItemIndex
                    if (idx >= 0 && idx < c.mediaItemCount) {
                        runCatching {
                            val item = c.getMediaItemAt(idx)
                            val btMeta = item.mediaMetadata.buildUpon()
                                .setTitle("♪ $currentLyric")
                                .setArtist("$currentTitle • $currentArtist")
                                .build()
                            c.replaceMediaItem(idx, item.buildUpon().setMediaMetadata(btMeta).build())
                        }
                    }
                }
            }
            delay(45)
        }
    }

    // Auto-Lyrics: Prioritaskan Enhanced LRC dari File Lokal -> Cache -> Lyricsify.com & LRCLIB
    LaunchedEffect(currentTitle, currentArtist, currentUri, autoLyrics) {
        val rawT = currentTitle.trim(); val rawA = currentArtist.trim()
        if (rawT.isBlank() || rawT == "Belum ada lagu" || rawT == "Judul tidak diketahui") return@LaunchedEffect
        val (cleanT, cleanA) = cleanSearchQuery(rawT, rawA)
        if (searchTrack.isBlank()) { searchTrack = cleanT; searchArtist = cleanA }
        val key = lyricsCacheKey(rawT, rawA)
        if (prefs.getString("current_lrc_key", "") != key) { lrcText = ""; currentLyric = ""; prefs.edit().putString("current_lrc", "").apply() }

        val activeSong = songs.firstOrNull { it.uri == currentUri }
        val localSidecar = activeSong?.filePath?.let { tryReadLocalSidecarLrc(it) }
        if (localSidecar != null) {
            lrcText = localSidecar
            saveLrcToCache(localSidecar, rawT, rawA)
            lyricsStatus = if (LyricsifyExtractor.hasEnhancedKaraokeTags(localSidecar)) "Enhanced LRC ★ dimuat dari folder lokal" else "Lirik .LRC dimuat dari folder lokal"
            return@LaunchedEffect
        }

        val cache = runCatching { JSONObject(prefs.getString("lyrics_cache_json", "{}").orEmpty()) }.getOrElse { JSONObject() }
        val cached = cache.optString(key, "").takeIf { it.isNotBlank() && it != "null" }
        if (cached != null) {
            lrcText = cached
            prefs.edit().putString("current_lrc", cached).putString("current_lrc_key", key).apply()
            lyricsStatus = if (LyricsifyExtractor.hasEnhancedKaraokeTags(cached)) "Enhanced LRC ★ dimuat dari cache lokal" else "Lirik dimuat dari cache lokal"
            return@LaunchedEffect
        }

        if (!autoLyrics) { lyricsStatus = "Lirik otomatis nonaktif."; return@LaunchedEffect }
        isSearchingLyrics = true; lyricsStatus = "Mencari Enhanced LRC di Lyricsify & LRCLIB: $cleanT..."
        try {
            val candidates = smartLyricsSearch(rawT, rawA)
            val found = candidates.firstOrNull()
            if (found != null && lyricsCacheKey(currentTitle, currentArtist) == key) {
                val lyrics = found.syncedLyrics ?: found.plainLyrics.orEmpty()
                lrcText = lyrics; results = candidates; saveLrcToCache(lyrics, rawT, rawA)
                val isEnh = LyricsifyExtractor.hasEnhancedKaraokeTags(lyrics)
                val srcName = if (found.album.orEmpty().contains("Lyricsify", true)) "Lyricsify.com" else "LRCLIB"
                lyricsStatus = when {
                    isEnh -> "Enhanced LRC ★ ditemukan dari $srcName!"
                    !found.syncedLyrics.isNullOrBlank() -> "Synced LRC ditemukan dari $srcName"
                    else -> "Lirik teks ditemukan dari $srcName"
                }
            } else if (lyricsCacheKey(currentTitle, currentArtist) == key) lyricsStatus = "Lirik belum ditemukan otomatis. Ketuk tab 'Cari Online'."
        } catch (e: Exception) { if (lyricsCacheKey(currentTitle, currentArtist) == key) lyricsStatus = "Pencarian gagal: ${e.message}" }
        finally { isSearchingLyrics = false }
    }

    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes > 0) {
            delay(sleepMinutes * 60_000L)
            val c = controller
            if (c != null && smoothFadePause) {
                for (step in 10 downTo 1) { runCatching { c.volume = step * 0.1f }; delay(120) }
                c.pause()
                runCatching { c.volume = 1.0f }
            } else c?.pause()
            sleepMinutes = 0; status = "Timer tidur selesai (musik dijeda dengan halus)."
        }
    }

    val durationFilteredSongs = remember(songs, minDurationSec) {
        val minMs = minDurationSec * 1000L
        songs.filter { it.isSaf || it.duration == 0L || it.duration >= minMs }
    }
    val sortedAndFilteredSongs = remember(durationFilteredSongs, search, sortOption, playCounts) {
        val base = if (search.isBlank()) durationFilteredSongs else durationFilteredSongs.filter {
            it.title.contains(search, true) || it.artist.contains(search, true) || it.album.contains(search, true) || it.folder.contains(search, true)
        }
        when (sortOption) {
            SortOption.DATE_ADDED_DESC -> base.sortedWith(compareByDescending<AudioTrack> { it.dateAddedSec }.thenBy { it.title.lowercase(Locale.ROOT) })
            SortOption.DATE_ADDED_ASC -> base.sortedWith(compareBy<AudioTrack> { it.dateAddedSec }.thenBy { it.title.lowercase(Locale.ROOT) })
            SortOption.TITLE_ASC -> base.sortedBy { it.title.lowercase(Locale.ROOT) }
            SortOption.TITLE_DESC -> base.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SortOption.ARTIST_ASC -> base.sortedBy { it.artist.lowercase(Locale.ROOT) }
            SortOption.DURATION_DESC -> base.sortedByDescending { it.duration }
            SortOption.DURATION_ASC -> base.sortedBy { it.duration }
            SortOption.MOST_PLAYED -> base.sortedByDescending { playCounts[it.id] ?: 0 }
        }
    }

    val folderGroups = remember(durationFilteredSongs) {
        durationFilteredSongs.groupBy { it.folder.ifBlank { "Penyimpanan Internal" } }.entries.sortedBy { it.key.lowercase(Locale.ROOT) }
    }

    val currentTrack = songs.firstOrNull { it.uri == currentUri }
    val recentTracks = lastPlayed.mapNotNull { id -> durationFilteredSongs.firstOrNull { it.id == id } }
    val favoriteTracks = sortedAndFilteredSongs.filter { it.id in favoriteIds }
    val topPlayedTracks = remember(durationFilteredSongs, playCounts) { durationFilteredSongs.filter { (playCounts[it.id] ?: 0) > 0 }.sortedByDescending { playCounts[it.id] ?: 0 }.take(25) }
    val recentlyAddedTracks = remember(durationFilteredSongs) { durationFilteredSongs.sortedByDescending { it.dateAddedSec }.take(30) }
    val neverPlayedTracks = remember(durationFilteredSongs, playCounts) { durationFilteredSongs.filter { (playCounts[it.id] ?: 0) == 0 } }

    val playlistTracks: List<AudioTrack> = when (selectedPlaylist) {
        "Favorit" -> favoriteTracks
        "Top 25 Sering Diputar" -> topPlayedTracks
        "Baru Ditambahkan" -> recentlyAddedTracks
        "Belum Pernah Diputar" -> neverPlayedTracks
        else -> {
            val activeName = selectedPlaylist
            if (activeName != null) playlists.firstOrNull { p -> p.name == activeName }?.trackIds?.mapNotNull { id -> songs.firstOrNull { s -> s.id == id } }.orEmpty() else emptyList()
        }
    }

    val (dynamicAccent, dynamicDarkBg) = remember(themePreset, currentTitle) { resolveThemeColors(themePreset, currentTitle) }
    val colors = if (darkMode) darkColorScheme(primary = dynamicAccent, secondary = dynamicAccent, background = Color(0xFF0B0D0A), surface = Color(0xFF161914), surfaceVariant = Color(0xFF232820))
    else lightColorScheme(primary = Color(0xFF4D6E16), secondary = Color(0xFF4D6E16))

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LyraBrandLogo(size = 38.dp, cornerRadius = 11.dp)
                            Column { Text("LYRA", fontWeight = FontWeight.Black); Text("LYRICSIFY · ENHANCED LRC · FFT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    },
                    actions = {
                        if (isRecordingKaraoke) {
                            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { toggleKaraokeRecording() }.padding(end = 6.dp)) {
                                Text("● REC ${formatTime(recordingSeconds * 1000L)}", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                        IconButton(onClick = { librarySubTab = LibrarySubTab.ALL; tab = LyraTab.LIBRARY }) { LyraVectorIcon(LyraIconType.SEARCH, tint = MaterialTheme.colorScheme.primary, size = 20.dp) }
                        TextButton(onClick = { if (ContextCompat.checkSelfPermission(context, permissionName()) == PackageManager.PERMISSION_GRANTED) scanSongs() else permissionLauncher.launch(permissionName()) }) { Text("Pindai") }
                    }
                )
            },
            bottomBar = {
                Column {
                    if ((currentUri != null || playing || currentTitle != "Belum ada lagu") && tab != LyraTab.NOW_PLAYING) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 8.dp, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                            Column(Modifier.fillMaxWidth().clickable { tab = LyraTab.NOW_PLAYING }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (currentTrack?.artworkUri != null) AsyncImage(model = currentTrack.artworkUri, contentDescription = null, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                    else LyraBrandLogo(size = 42.dp, cornerRadius = 12.dp)
                                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                        Text(currentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                        Text(currentLyric.ifBlank { currentArtist.ifBlank { "Ketuk untuk membuka layar putar" } }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { LyraVectorIcon(LyraIconType.PREV, tint = Color.White, size = 18.dp) }
                                    FilledTonalIconButton(onClick = { togglePlayWithFade() }) { LyraVectorIcon(if (playing) LyraIconType.PAUSE else LyraIconType.PLAY, tint = MaterialTheme.colorScheme.primary, size = 20.dp) }
                                    IconButton(onClick = { controller?.seekToNextMediaItem() }) { LyraVectorIcon(LyraIconType.NEXT, tint = Color.White, size = 18.dp) }
                                }
                                if (duration > 0) LinearProgressIndicator(progress = { (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        LyraTab.values().forEach { item ->
                            val isSelected = tab == item
                            val iconType = when (item) { LyraTab.HOME -> LyraIconType.HOME; LyraTab.NOW_PLAYING -> if (playing) LyraIconType.PAUSE else LyraIconType.PLAY; LyraTab.LIBRARY -> LyraIconType.MUSIC; LyraTab.LYRICS -> LyraIconType.LYRICS; LyraTab.SETTINGS -> LyraIconType.SETTINGS }
                            NavigationBarItem(
                                selected = isSelected, onClick = { tab = item },
                                icon = { LyraVectorIcon(iconType, tint = if (isSelected) Color(0xFF0B0D0A) else MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp) },
                                label = { Text(item.label, maxLines = 1, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                when (tab) {
                    LyraTab.HOME -> {
                        var ytmHomeSections by remember { mutableStateOf<List<com.example.lyraplayer.lyrics.YtmHomeSection>>(emptyList()) }
                        var isLoadingHome by remember { mutableStateOf(true) }

                        LaunchedEffect(Unit) {
                            if (ytmHomeSections.isEmpty()) {
                                runCatching {
                                    val secs = withContext(Dispatchers.IO) { YtmClient.fetchHomeSections() }
                                    ytmHomeSections = secs
                                }.onFailure {
                                    ytmStatus = "Gagal memuat beranda YT Music"
                                }
                                isLoadingHome = false
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                            item {
                                Spacer(Modifier.height(6.dp))
                                ElevatedCard(onClick = { tab = LyraTab.NOW_PLAYING }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (currentTrack?.artworkUri != null) AsyncImage(model = currentTrack.artworkUri, contentDescription = null, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
                                        else LyraBrandLogo(size = 76.dp, cornerRadius = 20.dp)
                                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                            Text("PEMUTAR UTAMA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                            Text(currentTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(currentArtist.ifBlank { "Ketuk untuk membuka pemutar" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("Lokal + YouTube Music Streaming", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                                        }
                                        FilledTonalIconButton(onClick = { if (currentUri != null || playing) togglePlayWithFade() else if (sortedAndFilteredSongs.isNotEmpty()) playTracks(sortedAndFilteredSongs, 0) }, modifier = Modifier.size(48.dp)) {
                                            LyraVectorIcon(if (playing) LyraIconType.PAUSE else LyraIconType.PLAY, tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                                        }
                                    }
                                }
                            }

                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    SummaryCard("Lokal (${minDurationSec}s+)", durationFilteredSongs.size.toString(), Modifier.weight(1f)) { librarySubTab = LibrarySubTab.ALL; tab = LyraTab.LIBRARY }
                                    SummaryCard("YT Music", "Online", Modifier.weight(1f)) { librarySubTab = LibrarySubTab.YT_MUSIC; tab = LyraTab.LIBRARY }
                                    SummaryCard("Folder", folderGroups.size.toString(), Modifier.weight(1f)) { librarySubTab = LibrarySubTab.FOLDERS; tab = LyraTab.LIBRARY }
                                }
                            }

                            // Bagian Beranda Rekomendasi YouTube Music ala Spotify
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Rekomendasi YouTube Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { librarySubTab = LibrarySubTab.YT_MUSIC; tab = LyraTab.LIBRARY }) { Text("Cari Lainnya â†’") }
                                }
                            }

                            if (isLoadingHome) {
                                item {
                                    Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                items(ytmHomeSections, key = { it.title }) { section ->
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                                        
                                        // Baris horizontal scroll ala Spotify Carousel
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            for (item in section.items) {
                                                ElevatedCard(
                                                    onClick = {
                                                        if (item.videoId.isNotBlank() && !item.videoId.startsWith("browse_")) {
                                                            val tr = ytmItemToAudioTrack(item)
                                                            playTracks(listOf(tr), 0)
                                                            status = "Streaming: ${tr.title}"
                                                        } else {
                                                            ytmQuery = item.title
                                                            triggerYtmSearch(item.title)
                                                            librarySubTab = LibrarySubTab.YT_MUSIC
                                                            tab = LyraTab.LIBRARY
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.width(135.dp)
                                                ) {
                                                    Column(Modifier.padding(8.dp)) {
                                                        Surface(
                                                            shape = RoundedCornerShape(12.dp),
                                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                                            modifier = Modifier.size(119.dp)
                                                        ) {
                                                            if (item.thumbnailUrl.isNotBlank()) {
                                                                AsyncImage(model = item.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                            } else {
                                                                Box(contentAlignment = Alignment.Center) { LyraBrandLogo(size = 54.dp, cornerRadius = 14.dp) }
                                                            }
                                                        }
                                                        Spacer(Modifier.height(6.dp))
                                                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                        Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                                    Text("Baru diputar di perangkat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { librarySubTab = LibrarySubTab.ALL; tab = LyraTab.LIBRARY }) { Text("Semua") }
                                }
                            }
                            if (recentTracks.isNotEmpty()) {
                                items(recentTracks.take(8), key = { "recent_" + it.id }) { song ->
                                    val isCurrent = song.uri == currentUri
                                    val isFav = song.id in favoriteIds
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                            .background(if (isCurrent) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else Color.Transparent)
                                            .clickable { playTracks(recentTracks, recentTracks.indexOfFirst { it.id == song.id }) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(42.dp)) {
                                            if (song.artworkUri != null) AsyncImage(model = song.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            else Box(contentAlignment = Alignment.Center) { LyraVectorIcon(LyraIconType.MUSIC, tint = MaterialTheme.colorScheme.secondary, size = 18.dp) }
                                        }
                                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { toggleFavorite(song) }, modifier = Modifier.size(32.dp)) {
                                            LyraVectorIcon(if (isFav) LyraIconType.HEART_FILLED else LyraIconType.HEART_OUTLINE, tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LyraTab.NOW_PLAYING -> {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(shape = RoundedCornerShape(16.dp), color = if (!showFullKaraokeInPlayer) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.clickable { showFullKaraokeInPlayer = false }) {
                                    Text(if (vinylMode) "Vinyl & Spektrum" else "Sampul & Spektrum", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (!showFullKaraokeInPlayer) Color(0xFF101010) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(16.dp), color = if (showFullKaraokeInPlayer) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.clickable { showFullKaraokeInPlayer = true }) {
                                    Text(if (hasEnhancedKaraoke) "Karaoke LRC ★" else "Lirik Karaoke", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (showFullKaraokeInPlayer) Color(0xFF101010) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isRecordingKaraoke) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { toggleKaraokeRecording() }
                                ) {
                                    Text(
                                        if (isRecordingKaraoke) "■ Stop (${formatTime(recordingSeconds * 1000L)})" else "● Rec Mic",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isRecordingKaraoke) Color.White else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                                TextButton(onClick = { showAudioLabDialog = true }) {
                                    val pitchLabel = if (pitchSemitones > 0) "+$pitchSemitones" else "$pitchSemitones"
                                    Text("${playbackSpeed}x·$pitchLabel", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                IconButton(onClick = { librarySubTab = LibrarySubTab.QUEUE; tab = LyraTab.LIBRARY }) { LyraVectorIcon(LyraIconType.QUEUE, tint = MaterialTheme.colorScheme.primary, size = 20.dp) }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (!showFullKaraokeInPlayer) {
                            Box(
                                Modifier.fillMaxWidth().weight(0.38f).padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(28.dp))
                                    .background(Brush.linearGradient(listOf(dynamicDarkBg, Color(0xFF151E0D), Color(0xFF0C1009)))),
                                contentAlignment = Alignment.Center
                            ) {
                                if (vinylMode) {
                                    SpinningVinylStage(
                                        artworkUri = currentTrack?.artworkUri,
                                        albumTitle = currentAlbum,
                                        isPlaying = playing,
                                        accentColor = dynamicAccent,
                                        onClick = { showFullKaraokeInPlayer = true },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().clickable { showFullKaraokeInPlayer = true }, contentAlignment = Alignment.Center) {
                                        if (currentTrack?.artworkUri != null) AsyncImage(model = currentTrack.artworkUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        else Column(horizontalAlignment = Alignment.CenterHorizontally) { LyraBrandLogo(size = 100.dp, cornerRadius = 26.dp); Spacer(Modifier.height(8.dp)); Text(currentAlbum.ifBlank { "LYRA KARAOKE" }.uppercase(), color = dynamicAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black) }
                                        Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(14.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) { Text("Ketuk untuk Lirik Karaoke ♪", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) }
                                    }
                                }
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clickable {
                                        vinylMode = !vinylMode; prefs.edit().putBoolean("vinyl_mode", vinylMode).apply()
                                    }
                                ) {
                                    Text(if (vinylMode) "Mode: Piringan Vinyl ↻" else "Mode: Sampul Penuh ↻", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                                }
                            }
                            KaraokeMiniStage(parsedKaraokeLines, position, hasEnhancedKaraoke, autoWordKaraoke, dynamicAccent, { showFullKaraokeInPlayer = true }, { val (cT, cA) = cleanSearchQuery(currentTitle, currentArtist); searchTrack = cT; searchArtist = cA; lyricsSubTab = LyricsSubTab.SEARCH; tab = LyraTab.LYRICS; triggerManualLyricsSearch(cT, cA) })
                        } else {
                            KaraokeFullStage(
                                lines = parsedKaraokeLines,
                                positionMs = position,
                                hasEnhancedTag = hasEnhancedKaraoke,
                                autoWordKaraoke = autoWordKaraoke,
                                accentColor = dynamicAccent,
                                darkBgColor = dynamicDarkBg,
                                onToggleAutoWord = { autoWordKaraoke = !autoWordKaraoke; prefs.edit().putBoolean("auto_word_karaoke", autoWordKaraoke).apply() },
                                onShareClick = { showShareQuoteDialog = true },
                                onSeekTo = { ms -> controller?.seekTo(ms); position = ms },
                                onEditLyricsClick = { val (cT, cA) = cleanSearchQuery(currentTitle, currentArtist); searchTrack = cT; searchArtist = cA; lyricsSubTab = LyricsSubTab.SEARCH; tab = LyraTab.LYRICS; triggerManualLyricsSearch(cT, cA) },
                                modifier = Modifier.fillMaxWidth().weight(0.50f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f).clickable { openMetadataEditor(currentTrack) }) {
                                Text(currentTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${currentArtist.ifBlank { "Artis tidak diketahui" }} · ${currentAlbum.ifBlank { "Album tidak diketahui" }}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                
                                // Tombol Unduh Instan jika lagu yang diputar berasal dari YouTube Music Online
                                val isYtmCurrent = currentUri?.toString()?.startsWith("ytm://") == true || currentTrack?.id?.startsWith("ytm:") == true
                                if (isYtmCurrent) {
                                    Spacer(Modifier.height(2.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.clickable {
                                            val videoId = currentUri?.host ?: currentUri?.lastPathSegment ?: currentTrack?.id?.removePrefix("ytm:") ?: ""
                                            if (videoId.isNotBlank()) {
                                                scope.launch(Dispatchers.IO) {
                                                    val success = YtmClient.downloadSongToDevice(
                                                        context,
                                                        YtmTrackItem(videoId, currentTitle, currentArtist, currentAlbum, duration, "", currentTrack?.artworkUri?.toString().orEmpty())
                                                    )
                                                    withContext(Dispatchers.Main) {
                                                        status = if (success) "Unduhan disimpan ke folder Music/LyraDownloads!" else "Gagal mengunduh lagu."
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            "↓ Unduh Lagu Ini ke Perangkat",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            val isFavNow = currentTrack?.id?.let { it in favoriteIds } == true
                            IconButton(onClick = { currentTrack?.let { toggleFavorite(it) } }) { LyraVectorIcon(if (isFavNow) LyraIconType.HEART_FILLED else LyraIconType.HEART_OUTLINE, tint = if (isFavNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, size = 24.dp) }
                            FilledTonalIconButton(onClick = { openMetadataEditor(currentTrack) }) { LyraVectorIcon(LyraIconType.EDIT, tint = MaterialTheme.colorScheme.primary, size = 18.dp) }
                        }
                        if (duration > 0) {
                            Slider(value = position.coerceIn(0L, duration).toFloat(), onValueChange = { position = it.toLong() }, onValueChangeFinished = { controller?.seekTo(position) }, valueRange = 0f..duration.toFloat(), modifier = Modifier.height(24.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(formatTime(position), style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("↺ 10s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { val t = (position - 10_000L).coerceAtLeast(0L); controller?.seekTo(t); position = t })
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (loopStartMs != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.clickable {
                                            when {
                                                loopStartMs == null -> loopStartMs = position
                                                loopEndMs == null && position > (loopStartMs ?: 0L) + 500L -> loopEndMs = position
                                                else -> { loopStartMs = null; loopEndMs = null }
                                            }
                                        }
                                    ) {
                                        val loopText = when {
                                            loopStartMs != null && loopEndMs != null -> "A-B Aktif (${formatTime(loopStartMs!!)}-${formatTime(loopEndMs!!)}) ✕"
                                            loopStartMs != null -> "A: ${formatTime(loopStartMs!!)} → Set B"
                                            else -> "Loop A-B"
                                        }
                                        Text(loopText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                    Text("10s ↻", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { val t = (position + 10_000L).coerceAtMost(duration); controller?.seekTo(t); position = t })
                                }
                                Text("−${formatTime((duration - position).coerceAtLeast(0L))}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        AudioVisualizer(
                            audioSessionId = audioSessionId,
                            isPlaying = playing,
                            style = visualizerStyle,
                            accentColor = dynamicAccent,
                            onCycleStyle = {
                                val all = VisualizerStyle.values()
                                val next = all[(all.indexOf(visualizerStyle) + 1) % all.size]
                                visualizerStyle = next
                                prefs.edit().putString("visualizer_style", next.name).apply()
                            },
                            modifier = Modifier.fillMaxWidth().height(66.dp)
                        )
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = if (shuffle) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, modifier = Modifier.size(44.dp).clickable { val ns = !(controller?.shuffleModeEnabled ?: false); controller?.shuffleModeEnabled = ns; shuffle = ns }) {
                                Box(contentAlignment = Alignment.Center) { LyraVectorIcon(LyraIconType.SHUFFLE, tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) }
                            }
                            FilledTonalButton(onClick = { controller?.seekToPreviousMediaItem() }, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp)) { LyraVectorIcon(LyraIconType.PREV, tint = Color.White, size = 22.dp) }
                            Button(onClick = { if (currentUri != null || playing) togglePlayWithFade() else if (sortedAndFilteredSongs.isNotEmpty()) playTracks(sortedAndFilteredSongs, 0) }, modifier = Modifier.size(66.dp), shape = RoundedCornerShape(22.dp), contentPadding = PaddingValues(0.dp)) { LyraVectorIcon(if (playing) LyraIconType.PAUSE else LyraIconType.PLAY, tint = Color(0xFF0B0D0A), size = 28.dp) }
                            FilledTonalButton(onClick = { controller?.seekToNextMediaItem() }, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp)) { LyraVectorIcon(LyraIconType.NEXT, tint = Color.White, size = 22.dp) }
                            Surface(shape = CircleShape, color = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, modifier = Modifier.size(44.dp).clickable { val nx = when (repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; controller?.repeatMode = nx; repeatMode = nx }) {
                                Box(contentAlignment = Alignment.Center) { LyraVectorIcon(when (repeatMode) { Player.REPEAT_MODE_ONE -> LyraIconType.REPEAT_ONE; Player.REPEAT_MODE_ALL -> LyraIconType.REPEAT_ALL; else -> LyraIconType.REPEAT_OFF }, tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) }
                            }
                        }
                    }

                    LyraTab.LIBRARY -> {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            LibrarySubTab.values().forEach { sub ->
                                val active = librarySubTab == sub
                                Surface(shape = RoundedCornerShape(14.dp), color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.weight(1f).clickable { librarySubTab = sub }) {
                                    Text(sub.title, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (active) Color(0xFF101010) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        when (librarySubTab) {
                            LibrarySubTab.ALL, LibrarySubTab.FAVORITES -> {
                                OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Cari judul, artis, album, atau folder") }, singleLine = true, shape = RoundedCornerShape(20.dp))
                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilterChip(selected = minDurationSec > 0, onClick = { val st = listOf(0, 15, 30, 45, 60, 90); val nx = st[(st.indexOf(minDurationSec) + 1) % st.size]; minDurationSec = nx; prefs.edit().putInt("min_duration_sec", nx).apply() }, label = { Text(if (minDurationSec == 0) "Durasi: Semua" else "Minimal: ≥${minDurationSec} dtk") })
                                    SortOption.values().forEach { opt -> FilterChip(selected = sortOption == opt, onClick = { sortOption = opt; prefs.edit().putString("sort_option", opt.name).apply() }, label = { Text(opt.label) }) }
                                }
                                val listToShow = if (librarySubTab == LibrarySubTab.FAVORITES) favoriteTracks else sortedAndFilteredSongs
                                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${listToShow.size} lagu · ${sortOption.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    TextButton(onClick = { if (listToShow.isNotEmpty()) playTracks(listToShow, 0) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("▶ Putar Semua") }
                                }
                                TrackList(listToShow, currentUri, playing, favoriteIds, ::playTracks, ::toggleFavorite, { trackToAdd = it }, { openMetadataEditor(it) }, true, Modifier.weight(1f))
                            }
                            LibrarySubTab.YT_MUSIC -> {
                                Spacer(Modifier.height(8.dp))
                                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(ytmStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = ytmQuery,
                                                onValueChange = { ytmQuery = it },
                                                label = { Text("Cari lagu / penyanyi di YT Music...") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = { triggerYtmSearch(ytmQuery) },
                                                enabled = ytmQuery.isNotBlank() && !isSearchingYtm,
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text(if (isSearchingYtm) "..." else "Cari")
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                            listOf("Chart Indonesia", "Dewa 19", "Pop Punk Indonesia", "Lagu Hits Terbaru", "Acoustic Chill").forEach { preset ->
                                                AssistChip(
                                                    onClick = { ytmQuery = preset; triggerYtmSearch(preset) },
                                                    label = { Text(preset, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                                if (isSearchingYtm) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Spacer(Modifier.height(8.dp))
                                }
                                val ytmAudioTracks = remember(ytmResults) { ytmResults.map { ytmItemToAudioTrack(it) } }
                                if (ytmAudioTracks.isEmpty() && !isSearchingYtm) {
                                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                        EmptyState("Streaming YouTube Music", "Ketik judul lagu atau pilih kategori di atas untuk memutar lagu online lengkap dengan lirik karaoke otomatis.")
                                    }
                                } else {
                                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${ytmAudioTracks.size} lagu online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        TextButton(
                                            onClick = {
                                                val first = ytmResults.firstOrNull()
                                                if (first != null) loadYtmRadio(first.videoId, first.title)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) { Text("↻ Muat Radio Serupa") }
                                    }
                                    TrackList(
                                        tracks = ytmAudioTracks,
                                        currentUri = currentUri,
                                        playing = playing,
                                        favoriteIds = favoriteIds,
                                        playTracks = { list, idx ->
                                            playTracks(list, idx)
                                            status = "Streaming YT Music: ${list[idx].title}"
                                        },
                                        toggleFavorite = ::toggleFavorite,
                                        onAddToPlaylist = { trackToAdd = it },
                                        onEditTrack = { openMetadataEditor(it) },
                                        showFolder = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            LibrarySubTab.FOLDERS -> {
                                Spacer(Modifier.height(8.dp))
                                val activeFolder = selectedBrowseFolder
                                if (activeFolder != null) {
                                    val folderTracks = durationFilteredSongs.filter { (it.folder.ifBlank { "Penyimpanan Internal" }) == activeFolder }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { selectedBrowseFolder = null }) { Text("← Semua Folder") }
                                        Button(onClick = { if (folderTracks.isNotEmpty()) playTracks(folderTracks, 0) }) { Text("▶ Putar Folder (${folderTracks.size})") }
                                    }
                                    Text(activeFolder.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(activeFolder, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                                    TrackList(folderTracks, currentUri, playing, favoriteIds, ::playTracks, ::toggleFavorite, { trackToAdd = it }, { openMetadataEditor(it) }, false, Modifier.weight(1f))
                                } else {
                                    Text("Penjelajah Folder Musik (${folderGroups.size} Folder)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        items(folderGroups, key = { it.key }) { (folderPath, list) ->
                                            ElevatedCard(onClick = { selectedBrowseFolder = folderPath }) {
                                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    LyraBrandLogo(size = 42.dp, cornerRadius = 12.dp)
                                                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                                        Text(folderPath.substringAfterLast('/').ifBlank { folderPath }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text("${list.size} lagu · $folderPath", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                    FilledTonalIconButton(onClick = { playTracks(list, 0) }) { LyraVectorIcon(LyraIconType.PLAY, tint = MaterialTheme.colorScheme.primary, size = 18.dp) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LibrarySubTab.PLAYLISTS -> {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) { Text("Smart & Custom Playlist", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = { showCreatePlaylist = true }) { Text("+ Buat Playlist") } }
                                Spacer(Modifier.height(8.dp))
                                if (selectedPlaylist != null) {
                                    val isSmart = selectedPlaylist in setOf("Favorit", "Top 25 Sering Diputar", "Baru Ditambahkan", "Belum Pernah Diputar")
                                    TextButton(onClick = { selectedPlaylist = null }) { Text("← Kembali ke daftar playlist") }
                                    Text(selectedPlaylist.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                                        Button(onClick = { playTracks(playlistTracks, 0) }, enabled = playlistTracks.isNotEmpty()) { Text("▶ Putar") }
                                        if (!isSmart) {
                                            OutlinedButton(onClick = { val p = playlists.firstOrNull { it.name == selectedPlaylist }; if (p != null) { val allIds = durationFilteredSongs.map { s -> s.id }; playlists = playlists.map { item -> if (item.name == p.name) item.copy(trackIds = (item.trackIds + allIds).distinct()) else item }; savePlaylists() } }) { Text("+ Semua Lagu") }
                                            OutlinedButton(onClick = { playlists = playlists.filterNot { it.name == selectedPlaylist }; savePlaylists(); selectedPlaylist = null }) { Text("Hapus") }
                                        }
                                    }
                                    TrackList(playlistTracks, currentUri, playing, favoriteIds, ::playTracks, ::toggleFavorite, { trackToAdd = it }, { openMetadataEditor(it) }, false, Modifier.weight(1f))
                                } else {
                                    val smartEntries = listOf(
                                        Triple("Favorit", "Lagu yang Anda sukai", favoriteTracks),
                                        Triple("Top 25 Sering Diputar", "Paling sering Anda dengarkan", topPlayedTracks),
                                        Triple("Baru Ditambahkan", "30 lagu terbaru di perangkat", recentlyAddedTracks),
                                        Triple("Belum Pernah Diputar", "Koleksi yang belum tersentuh", neverPlayedTracks)
                                    )
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        items(smartEntries) { (name, desc, list) ->
                                            ElevatedCard(onClick = { selectedPlaylist = name }) {
                                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    LyraVectorIcon(if (name == "Favorit") LyraIconType.HEART_FILLED else LyraIconType.MUSIC, tint = MaterialTheme.colorScheme.primary, size = 24.dp)
                                                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(name, fontWeight = FontWeight.Bold); Text("$desc · ${list.size} lagu", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                    FilledTonalIconButton(onClick = { playTracks(list, 0) }, enabled = list.isNotEmpty()) { LyraVectorIcon(LyraIconType.PLAY, tint = MaterialTheme.colorScheme.primary, size = 18.dp) }
                                                }
                                            }
                                        }
                                        items(playlists, key = { it.name }) { p ->
                                            ElevatedCard(onClick = { selectedPlaylist = p.name }) {
                                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    LyraVectorIcon(LyraIconType.QUEUE, tint = MaterialTheme.colorScheme.primary, size = 24.dp)
                                                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(p.name, fontWeight = FontWeight.Bold); Text("Playlist Kustom · ${p.trackIds.size} lagu", style = MaterialTheme.typography.bodySmall) }
                                                    FilledTonalIconButton(onClick = { playTracks(p.trackIds.mapNotNull { id -> songs.firstOrNull { it.id == id } }, 0) }) { LyraVectorIcon(LyraIconType.PLAY, tint = MaterialTheme.colorScheme.primary, size = 18.dp) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LibrarySubTab.QUEUE -> {
                                Spacer(Modifier.height(8.dp))
                                Text("Antrean Pemutaran Interaktif", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val queue = controller?.let { c -> (0 until c.mediaItemCount).mapNotNull { i -> val item = c.getMediaItemAt(i); val uri = item.localConfiguration?.uri ?: return@mapNotNull null; val matched = songs.firstOrNull { it.id == item.mediaId || it.uri == uri }; applyTrackOverride(AudioTrack(item.mediaId.ifBlank { uri.toString() }, uri, item.mediaMetadata.title?.toString() ?: "Audio", item.mediaMetadata.artist?.toString() ?: "Artis", item.mediaMetadata.albumTitle?.toString() ?: "", 0L, filePath = matched?.filePath.orEmpty())) } }.orEmpty()
                                if (queue.isEmpty()) EmptyState("Antrean kosong", "Pilih lagu untuk mulai memutar.")
                                else LazyColumn(Modifier.weight(1f)) {
                                    items(queue.indices.toList(), key = { queue[it].id + "_$it" }) { idx ->
                                        val tr = queue[idx]; val isCur = tr.uri == currentUri
                                        Row(Modifier.fillMaxWidth().clickable { controller?.seekTo(idx, 0L); controller?.play() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (isCur) "▶" else "${idx + 1}", Modifier.width(28.dp), color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                            Column(Modifier.weight(1f)) { Text(tr.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal); Text(tr.artist, style = MaterialTheme.typography.bodySmall) }
                                            if (idx > 0) TextButton(onClick = { controller?.moveMediaItem(idx, idx - 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(30.dp)) { Text("↑") }
                                            if (idx < queue.lastIndex) TextButton(onClick = { controller?.moveMediaItem(idx, idx + 1) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(30.dp)) { Text("↓") }
                                            IconButton(onClick = { openMetadataEditor(tr) }, modifier = Modifier.size(32.dp)) { LyraVectorIcon(LyraIconType.EDIT, size = 16.dp) }
                                            TextButton(onClick = { controller?.removeMediaItem(idx) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("✕") }
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    LyraTab.LYRICS -> {
                        Spacer(Modifier.height(8.dp))
                        ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(currentTitle, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(currentArtist.ifBlank { "Artis tidak diketahui" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(lyricsStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 3.dp))
                                }
                                OutlinedButton(onClick = { lrcPicker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) { Text("Impor .LRC", fontSize = 11.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LyricsSubTab.values().forEach { sub ->
                                val active = lyricsSubTab == sub
                                Surface(shape = RoundedCornerShape(14.dp), color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.weight(1f).clickable { lyricsSubTab = sub }) {
                                    Text(sub.title, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (active) Color(0xFF101010) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        when (lyricsSubTab) {
                            LyricsSubTab.PREVIEW -> {
                                KaraokeFullStage(
                                    lines = parsedKaraokeLines,
                                    positionMs = position,
                                    hasEnhancedTag = hasEnhancedKaraoke,
                                    autoWordKaraoke = autoWordKaraoke,
                                    accentColor = dynamicAccent,
                                    darkBgColor = dynamicDarkBg,
                                    onToggleAutoWord = { autoWordKaraoke = !autoWordKaraoke; prefs.edit().putBoolean("auto_word_karaoke", autoWordKaraoke).apply() },
                                    onShareClick = { showShareQuoteDialog = true },
                                    onSeekTo = { ms -> controller?.seekTo(ms); position = ms },
                                    onEditLyricsClick = { val (cT, cA) = cleanSearchQuery(currentTitle, currentArtist); searchTrack = cT; searchArtist = cA; lyricsSubTab = LyricsSubTab.SEARCH; triggerManualLyricsSearch(cT, cA) },
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                    OutlinedButton(onClick = { lrcText = EnhancedLrcEngine.shiftAllTags(lrcText, -500); saveLrcToCache(lrcText) }, modifier = Modifier.weight(1f)) { Text("−0,5 dtk") }
                                    OutlinedButton(onClick = { lrcText = EnhancedLrcEngine.shiftAllTags(lrcText, 500); saveLrcToCache(lrcText) }, modifier = Modifier.weight(1f)) { Text("+0,5 dtk") }
                                    Button(onClick = { lyricsSubTab = LyricsSubTab.EDITOR }, modifier = Modifier.weight(1.2f)) { Text("✎ Edit LRC") }
                                }
                            }
                            LyricsSubTab.SEARCH -> {
                                ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Sumber: Lyricsify.com & LRCLIB (Prioritas Enhanced LRC ★)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(searchTrack, { searchTrack = it }, label = { Text("Judul Lagu / Link URL Lyricsify") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1.3f))
                                            OutlinedTextField(searchArtist, { searchArtist = it }, label = { Text("Artis (Opsional)") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(0.9f))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(onClick = { val (cT, cA) = cleanSearchQuery(currentTitle, currentArtist); searchTrack = cT; searchArtist = cA; triggerManualLyricsSearch(cT, cA) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("↻ Lagu Saat Ini") }
                                            Button(onClick = { triggerManualLyricsSearch(searchTrack, searchArtist) }, enabled = searchTrack.isNotBlank() && !isSearchingLyrics, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1.3f)) { Text(if (isSearchingLyrics) "Mencari..." else "⌕ Cari Lyricsify & LRCLIB") }
                                        }
                                    }
                                }
                                if (isSearchingLyrics) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.primary) else Spacer(Modifier.height(8.dp))
                                if (results.isEmpty() && !isSearchingLyrics) {
                                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { EmptyState("Cari Lirik Karaoke", "Ketik judul lagu atau tempel langsung URL https://www.lyricsify.com/lyrics/... di atas.") }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
                                        items(results) { res ->
                                            val isSynced = !res.syncedLyrics.isNullOrBlank()
                                            val isEnhanced = LyricsifyExtractor.hasEnhancedKaraokeTags(res.syncedLyrics)
                                            val isFromLyricsify = res.album.orEmpty().contains("Lyricsify", true)
                                            val previewLines = (res.syncedLyrics ?: res.plainLyrics.orEmpty()).lines().map { EnhancedLrcEngine.stripEnhancedTags(it.substringAfterLast("]")).trim() }.filter { it.isNotBlank() }.take(2).joinToString(" / ")
                                            val nameStr = res.name.orEmpty(); val artistStr = res.artist.orEmpty(); val albumStr = res.album.orEmpty()
                                            ElevatedCard(onClick = { val chosen = res.syncedLyrics ?: res.plainLyrics.orEmpty(); lrcText = chosen; saveLrcToCache(chosen); lyricsStatus = "Lirik \"$nameStr\" disimpan"; lyricsSubTab = LyricsSubTab.PREVIEW }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                        Text(nameStr, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                        Surface(shape = RoundedCornerShape(10.dp), color = when { isEnhanced -> dynamicAccent; isSynced -> Color(0xFF3A5A20); else -> Color(0xFF3A3A3A) }) {
                                                            val badgeText = when {
                                                                isEnhanced && isFromLyricsify -> "LYRICSIFY ★ ENHANCED"
                                                                isEnhanced -> "ENHANCED KARAOKE ★"
                                                                isFromLyricsify && isSynced -> "LYRICSIFY SYNC"
                                                                isSynced -> "LRCLIB SYNC"
                                                                else -> "TEKS BIASA"
                                                            }
                                                            Text(badgeText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isEnhanced) Color(0xFF101010) else Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                                        }
                                                    }
                                                    Text("$artistStr${if (albumStr.isNotBlank()) " · $albumStr" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    if (previewLines.isNotBlank()) Text("\"$previewLines\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            LyricsSubTab.EDITOR -> {
                                ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Waktu Lagu: ${formatTime(position)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Terjemahan: gunakan ' // ' di akhir baris", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                            FilledTonalButton(onClick = { lrcText = (lrcText.trimEnd() + "\n" + EnhancedLrcEngine.formatLineTag(position) + " ").trimStart() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+ [Baris] ${EnhancedLrcEngine.formatLineTag(position)}", fontSize = 11.sp) }
                                            FilledTonalButton(onClick = { lrcText = lrcText.trimEnd() + " " + EnhancedLrcEngine.formatWordTag(position) + " " }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Text("+ <Kata> ${EnhancedLrcEngine.formatWordTag(position)}", fontSize = 11.sp) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(value = lrcText, onValueChange = { lrcText = it }, modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(16.dp), label = { Text("Editor Teks .LRC / Enhanced LRC / Dual Terjemahan") })
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                    OutlinedButton(onClick = { saveLrcLauncher.launch(currentTitle.substringBeforeLast('.', currentTitle) + ".lrc") }, Modifier.weight(1f)) { Text("Ekspor") }
                                    OutlinedButton(onClick = { val uri = currentUri ?: return@OutlinedButton; scope.launch { val r = runCatching { withContext(Dispatchers.IO) { AudioTagEditor.embedLyrics(context, uri, lrcText) } }; lyricsStatus = if (r.isSuccess) "Lirik ditanamkan ke file." else "Gagal tanam tag." } }, Modifier.weight(1f)) { Text("Tanam Tag") }
                                    Button(onClick = { saveLrcToCache(lrcText); LyricsWidgetProvider.updateAll(context, currentTitle, EnhancedLrcEngine.stripEnhancedTags(LrcParser.displayLine(lrcText, position))); lyricsStatus = "Lirik disimpan."; lyricsSubTab = LyricsSubTab.PREVIEW }, Modifier.weight(1.2f)) { Text("Simpan Lirik") }
                                }
                            }
                        }
                    }

                    LyraTab.SETTINGS -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                            item {
                                Text("Pengaturan & Personalisasi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                SettingRow("Tema gelap", "Gunakan antarmuka gelap khas Lyra", darkMode) { darkMode = it; prefs.edit().putBoolean("dark_mode", it).apply() }
                                SettingRow("Mode Piringan Hitam (Vinyl)", "Tampilkan animasi piringan hitam berputar & tonearm di layar putar", vinylMode) { vinylMode = it; prefs.edit().putBoolean("vinyl_mode", it).apply() }
                                SettingRow("Smooth Fade-Out", "Turunkan volume secara halus saat jeda & timer tidur", smoothFadePause) { smoothFadePause = it; prefs.edit().putBoolean("smooth_fade", it).apply() }
                                SettingRow("Bluetooth Car / Watch Live Lyrics", "Kirim baris lirik aktif ke layar mobil atau smartwatch via metadata AVRCP", bluetoothLiveLyrics) { bluetoothLiveLyrics = it; prefs.edit().putBoolean("bt_live_lyrics", it).apply() }
                                SettingRow("Lirik otomatis (Lyricsify + LRCLIB)", "Cari Enhanced LRC otomatis dari folder lokal, cache, Lyricsify.com, & LRCLIB", autoLyrics) { autoLyrics = it; prefs.edit().putBoolean("auto_lyrics", it).apply() }
                                SettingRow("Auto-Word Karaoke (Estimasi Kata)", "Sorot kata demi kata otomatis untuk LRC biasa yang belum memiliki tag <mm:ss.xx>", autoWordKaraoke) { autoWordKaraoke = it; prefs.edit().putBoolean("auto_word_karaoke", it).apply() }
                            }
                            item {
                                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Tema Warna Aksen & Lirik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ThemeAccent.values().forEach { preset ->
                                                FilterChip(selected = themePreset == preset, onClick = { themePreset = preset; prefs.edit().putString("theme_preset", preset.name).apply() }, label = { Text(preset.label) })
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Filter Durasi Minimal Musik", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("Sembunyikan audio pendek (seperti voice note/notifikasi) di bawah durasi ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(0, 15, 30, 45, 60, 90).forEach { sec -> FilterChip(selected = minDurationSec == sec, onClick = { minDurationSec = sec; prefs.edit().putInt("min_duration_sec", sec).apply() }, label = { Text(if (sec == 0) "Semua" else "≥ $sec dtk") }) }
                                        }
                                    }
                                }
                            }
                            item {
                                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Audio, 3D Surround & Equalizer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); EqualizerPanel(audioSessionId = audioSessionId) }
                                }
                            }
                            item {
                                ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Backup & Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { backupExportLauncher.launch("lyra-backup.json") }) { Text("Ekspor backup") }; OutlinedButton(onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/*")) }) { Text("Pulihkan") } }
                                        OutlinedButton(onClick = { m3uExportLauncher.launch("lyra-playlist.m3u") }, Modifier.fillMaxWidth()) { Text("Ekspor M3U") }
                                        OutlinedButton(onClick = { m3uImportLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Impor M3U") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareQuoteDialog) {
        val activeIdx = parsedKaraokeLines.indexOfLast { it.startMs <= position }.coerceAtLeast(0)
        val quoteSnippet = parsedKaraokeLines.drop((activeIdx - 1).coerceAtLeast(0)).take(3).joinToString("\n") {
            if (it.translationText.isNotBlank()) "“${it.plainText}”\n(${it.translationText})" else "“${it.plainText}”"
        }.ifBlank { "“$currentLyric”" }
        AlertDialog(
            onDismissRequest = { showShareQuoteDialog = false },
            title = { Text("Bagikan Kutipan Lirik") },
            text = {
                ElevatedCard(shape = RoundedCornerShape(20.dp), colors = CardDefaults.elevatedCardColors(containerColor = dynamicDarkBg)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("♫ LYRA KARAOKE CARD", style = MaterialTheme.typography.labelSmall, color = dynamicAccent, fontWeight = FontWeight.Black)
                        Text(quoteSnippet, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Text("$currentTitle — ${currentArtist.ifBlank { "Lyra Player" }}", style = MaterialTheme.typography.bodySmall, color = dynamicAccent)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val shareText = "$quoteSnippet\n\n— $currentTitle ($currentArtist)\nDiputar di Lyra Karaoke Player ♫"
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching { context.startActivity(Intent.createChooser(sendIntent, "Bagikan Kutipan Lirik")) }
                    showShareQuoteDialog = false
                }) { Text("Bagikan Sekarang") }
            },
            dismissButton = { TextButton(onClick = { showShareQuoteDialog = false }) { Text("Tutup") } }
        )
    }

    if (showAudioLabDialog) {
        AlertDialog(
            onDismissRequest = { showAudioLabDialog = false },
            title = { Text("Studio Tempo & Nada (Pitch)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Atur kecepatan lagu dan naik/turunkan nada dasar (semitone) untuk menyesuaikan tinggi suara karaoke Anda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Kecepatan Tempo: ${playbackSpeed}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.25f, 1.5f).forEach { sp ->
                            FilterChip(selected = playbackSpeed == sp, onClick = { playbackSpeed = sp; applyPlaybackParams(sp, pitchSemitones) }, label = { Text("${sp}x") })
                        }
                    }
                    val pSign = if (pitchSemitones > 0) "+$pitchSemitones" else "$pitchSemitones"
                    Text("Nada Dasar (Pitch): $pSign Semitone", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (-6..6).forEach { semi ->
                            val lbl = if (semi > 0) "+$semi" else "$semi"
                            FilterChip(selected = pitchSemitones == semi, onClick = { pitchSemitones = semi; applyPlaybackParams(playbackSpeed, semi) }, label = { Text(lbl) })
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAudioLabDialog = false }) { Text("Selesai") } },
            dismissButton = {
                TextButton(onClick = {
                    playbackSpeed = 1.0f; pitchSemitones = 0
                    applyPlaybackParams(1.0f, 0)
                    showAudioLabDialog = false
                }) { Text("Reset Normal") }
            }
        )
    }

   if (showMetadataDialog) {
        val target = editingTrackTarget ?: currentTrack
        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            title = { Text("Edit Permanen Detail Lagu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Perubahan nama penyanyi, judul, dan album akan disimpan secara permanen untuk lagu ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(editTitle, { editTitle = it }, label = { Text("Judul lagu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editArtist, { editArtist = it }, label = { Text("Penyanyi / Artis") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(editAlbum, { editAlbum = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(editArtworkUri, { editArtworkUri = it }, label = { Text("URI Sampul") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { artworkPicker.launch(arrayOf("image/*")) }) { Text("Foto") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val title = editTitle.trim().ifBlank { target?.title ?: currentTitle }
                    val artist = editArtist.trim().ifBlank { "Artis tidak diketahui" }
                    val album = editAlbum.trim().ifBlank { "Album tidak diketahui" }
                    val artStr = editArtworkUri.trim()
                    val ov = MetadataOverride(title, artist, album, artStr)
                    val updatedMap = metadataOverrides.toMutableMap()

                    if (target != null) {
                        updatedMap[target.id] = ov
                        updatedMap[target.uri.toString()] = ov
                        if (target.filePath.isNotBlank()) updatedMap["path:${target.filePath}"] = ov
                    }
                    if (currentUri != null && (target == null || target.uri == currentUri)) {
                        updatedMap[currentUri.toString()] = ov
                    }

                    metadataOverrides = updatedMap
                    saveMetadataOverrides(prefs, updatedMap)

                    // >>> TARUH KODE PENYIMPANAN PERMANEN KE FILE FISIK DI SINI <<<
                    scope.launch(Dispatchers.IO) {
                        runCatching {
                            val mediaUri = target?.uri ?: currentUri
                            if (mediaUri != null) {
                                // 1. Perbarui database MediaStore Android
                                val values = ContentValues().apply {
                                    put(MediaStore.Audio.Media.TITLE, title)
                                    put(MediaStore.Audio.Media.ARTIST, artist)
                                    put(MediaStore.Audio.Media.ALBUM, album)
                                }
                                context.contentResolver.update(mediaUri, values, null, null)

                                // 2. Tulis permanen langsung ke file fisik audio menggunakan jaudiotagger
                                val filePath = target?.filePath
                                if (!filePath.isNullOrBlank()) {
                                    val file = File(filePath)
                                    if (file.exists() && file.canWrite()) {
                                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                                        val tag = audioFile.tagOrCreateAndSetDefault
                                        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                                        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
                                        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
                                        org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                                    }
                                }
                            }
                        }
                    }
                    // =============================================================

                    val mediaUri = target?.uri ?: currentUri
                    if (mediaUri != null && mediaUri.toString().startsWith("content://media/")) {
                        scope.launch(Dispatchers.IO) {val mediaUri = target?.uri ?: currentUri
                    if (mediaUri != null) {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                // 1. Tulis permanen langsung ke file fisik menggunakan jaudiotagger
                                val filePath = target?.filePath.takeIf { !it.isNullOrBlank() } 
                                    ?: run {
                                        // Coba ambil path asli dari URI jika filePath kosong
                                        val cursor = context.contentResolver.query(mediaUri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)
                                        cursor?.use {
                                            if (it.moveToFirst()) it.getString(0) else null
                                        }
                                    }

                                if (!filePath.isNullOrBlank()) {
                                    val file = File(filePath)
                                    if (file.exists() && file.canWrite()) {
                                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                                        val tag = audioFile.tagOrCreateAndSetDefault
                                        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                                        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
                                        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
                                        org.jaudiotagger.audio.AudioFileIO.write(audioFile)
                                    }
                                }

                                // 2. Perbarui database MediaStore Android
                                val values = ContentValues().apply {
                                    put(MediaStore.Audio.Media.TITLE, title)
                                    put(MediaStore.Audio.Media.ARTIST, artist)
                                    put(MediaStore.Audio.Media.ALBUM, album)
                                }
                                context.contentResolver.update(mediaUri, values, null, null)
                            }
                        }
                    }
                        }
                    }

                    songs = songs.map { s -> applyTrackOverride(s, updatedMap) }

                    if (target == null || target.uri == currentUri) {
                        currentTitle = title; currentArtist = artist; currentAlbum = album
                        val (cT, cA) = cleanSearchQuery(title, artist); searchTrack = cT; searchArtist = cA
                        prefs.edit().putString("current_title", title).putString("current_artist", artist).putString("current_album", album).commit()

                        val c = controller
                        val idx = c?.currentMediaItemIndex ?: -1
                        if (c != null && idx >= 0 && idx < c.mediaItemCount) {
                            val oldItem = c.getMediaItemAt(idx)
                            val artUri = artStr.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: oldItem.mediaMetadata.artworkUri
                            val newMeta = oldItem.mediaMetadata.buildUpon().setTitle(title).setArtist(artist).setAlbumTitle(album).setArtworkUri(artUri).build()
                            c.replaceMediaItem(idx, oldItem.buildUpon().setMediaMetadata(newMeta).build())
                        }
                    }
                    status = "Detail lagu \"$title\" disimpan permanen."
                    showMetadataDialog = false
                }) { Text("Simpan Permanen") }
            },
            // ... (sisa dismissButton biarkan tetap sama)
            dismissButton = {
                Row {
                    if (target != null) {
                        TextButton(onClick = {
                            val updatedMap = metadataOverrides.toMutableMap()
                            updatedMap.remove(target.id)
                            updatedMap.remove(target.uri.toString())
                            if (target.filePath.isNotBlank()) updatedMap.remove("path:${target.filePath}")
                            metadataOverrides = updatedMap
                            saveMetadataOverrides(prefs, updatedMap)
                            scanSongs()
                            showMetadataDialog = false
                        }) { Text("Reset") }
                    }
                    TextButton(onClick = { showMetadataDialog = false }) { Text("Batal") }
                }
            }
        )
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Buat playlist") },
            text = { OutlinedTextField(newPlaylistName, { newPlaylistName = it }, label = { Text("Nama playlist") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = newPlaylistName.trim()
                    if (name.isNotBlank() && playlists.none { it.name.equals(name, true) }) { playlists = playlists + listOf(LyraPlaylist(name, emptyList())); savePlaylists(); newPlaylistName = ""; showCreatePlaylist = false; selectedPlaylist = name }
                }) { Text("Buat") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Batal") } }
        )
    }

    if (trackToAdd != null) {
        AlertDialog(
            onDismissRequest = { trackToAdd = null },
            title = { Text("Tambahkan ke playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (pl in playlists) {
                        TextButton(onClick = {
                            val tr = trackToAdd
                            if (tr != null) { playlists = playlists.map { p -> if (p.name == pl.name) p.copy(trackIds = (p.trackIds + listOf(tr.id)).distinct()) else p }; savePlaylists() }
                            trackToAdd = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("${pl.name} · ${pl.trackIds.size} lagu") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { trackToAdd = null }) { Text("Tutup") } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Timer tidur") },
            text = {
                Column {
                    listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { sleepMinutes = m; showSleepDialog = false }) {
                            RadioButton(selected = sleepMinutes == m, onClick = { sleepMinutes = m; showSleepDialog = false }); Text("$m menit")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sleepMinutes = 0; showSleepDialog = false }) { Text("Matikan") } },
            dismissButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Tutup") } }
        )
    }
    // --- Letakkan di sini (di luar Composable manapun, misalnya di bagian bawah MainActivity.kt) ---
fun toggleFloatingLyric(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    } else {
        if (com.example.lyraplayer.playback.FloatingLyricService.isRunning) {
            context.stopService(Intent(context, com.example.lyraplayer.playback.FloatingLyricService::class.java))
        } else {
            context.startService(Intent(context, com.example.lyraplayer.playback.FloatingLyricService::class.java))
        }
    }
}
}
// === END OF MAINACTIVITY ===
