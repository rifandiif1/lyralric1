package com.example.lyraplayer.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class YtmTrackItem(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "YouTube Music",
    val durationMs: Long = 0L,
    val durationText: String = "",
    val thumbnailUrl: String = ""
)

data class YtmHomeSection(
    val title: String,
    val items: List<YtmTrackItem>
)

object YtmClient {
    private const val YTM_BASE = "https://music.youtube.com/youtubei/v1"
    private const val FILTER_SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"

    private fun postJson(urlStr: String, bodyObj: JSONObject, headers: Map<String, String>): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            for ((k, v) in headers) setRequestProperty(k, v)
        }
        return try {
            conn.outputStream.use { it.write(bodyObj.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("YTM HTTP $code")
            JSONObject(resp.ifBlank { "{}" })
        } finally {
            conn.disconnect()
        }
    }

    private fun postWebRemix(endpoint: String, payload: JSONObject): JSONObject {
        val ctx = JSONObject().put(
            "client", JSONObject()
                .put("clientName", "WEB_REMIX")
                .put("clientVersion", "1.20240101.00.00")
                .put("hl", "id")
                .put("gl", "ID")
        )
        payload.put("context", ctx)
        val headers = mapOf(
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        )
        return postJson("$YTM_BASE/$endpoint?prettyPrint=false", payload, headers)
    }

    fun searchSongs(query: String): List<YtmTrackItem> {
        if (query.isBlank()) return emptyList()
        val body = JSONObject().put("query", query.trim()).put("params", FILTER_SONGS)
        val root = postWebRemix("search", body)
        val out = mutableListOf<YtmTrackItem>()
        for (shelf in findAll(root, "musicShelfRenderer")) {
            val contents = shelf.optJSONArray("contents") ?: continue
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                parseListItem(item)?.let { out.add(it) }
            }
        }
        return out.distinctBy { it.videoId }
    }
// Fungsi untuk Mengunduh Lagu YouTube Music ke Folder Musik HP
    fun downloadSongToDevice(context: Context, track: YtmTrackItem): Boolean {
        return runCatching {
            val streamUrl = resolveStreamUrl(track.videoId) ?: return false
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val cleanTitle = track.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val fileName = "YT_Music_${cleanTitle}_${track.videoId}.m4a"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LyraDownloads")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { output ->
                    connection.inputStream.use { input -> input.copyTo(output) }
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "LyraDownloads")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.outputStream().use { output ->
                    connection.inputStream.use { input -> input.copyTo(output) }
                }
                // Scan media agar masuk ke database galeri HP
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                context.sendBroadcast(scanIntent)
            }
            true
        }.getOrDefault(false)
    }
    fun fetchHomeSections(): List<YtmHomeSection> {
        val body = JSONObject().put("browseId", "FEmusic_home")
        val root = postWebRemix("browse", body)
        val sections = mutableListOf<YtmHomeSection>()

        val sectionList = findFirst(root, "sectionListRenderer")
        val contents = sectionList?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val sec = contents.optJSONObject(i) ?: continue
            val carousel = sec.optJSONObject("musicCarouselShelfRenderer")
            if (carousel != null) {
                val headerTitle = extractText(findFirst(carousel.optJSONObject("header"), "title"))
                val carContents = carousel.optJSONArray("contents") ?: continue
                val tracks = mutableListOf<YtmTrackItem>()
                for (j in 0 until carContents.length()) {
                    val c = carContents.optJSONObject(j) ?: continue
                    val rItem = c.optJSONObject("musicResponsiveListItemRenderer")
                    val twoRow = c.optJSONObject("musicTwoRowItemRenderer")

                    if (rItem != null) {
                        parseListItem(rItem)?.let { tracks.add(it) }
                    } else if (twoRow != null) {
                        val title = extractText(twoRow.optJSONObject("title"))
                        val subtitle = extractText(twoRow.optJSONObject("subtitle"))
                        val thumb = extractBestThumbnail(twoRow.optJSONObject("thumbnailRenderer"))
                        val vid = findFirst(twoRow.optJSONObject("navigationEndpoint"), "watchEndpoint")?.optString("videoId").orEmpty()
                        if (title.isNotBlank()) {
                            tracks.add(YtmTrackItem(vid.ifBlank { "browse_${title.hashCode()}" }, title, subtitle, "YouTube Music", 0L, "", thumb))
                        }
                    }
                }
                if (tracks.isNotEmpty() && headerTitle.isNotBlank()) {
                    sections.add(YtmHomeSection(headerTitle, tracks))
                }
            }
        }
        return sections
    }

    fun fetchRadioTracks(seedVideoId: String = "dQw4w9WgXcQ"): List<YtmTrackItem> {
        val body = JSONObject()
            .put("videoId", seedVideoId)
            .put("playlistId", "RDAMVM$seedVideoId")
            .put("isAudioOnly", true)
        val root = postWebRemix("next", body)
        val out = mutableListOf<YtmTrackItem>()
        for (p in findAll(root, "playlistPanelVideoRenderer")) {
            val vid = p.optString("videoId")
            if (vid.isBlank()) continue
            val title = cleanDisplayTitle(extractText(p.optJSONObject("title")))
            val artist = extractText(p.optJSONObject("shortBylineText") ?: p.optJSONObject("longBylineText"))
            val durStr = extractText(p.optJSONObject("lengthText")).replace(".", ":")
            val thumb = extractBestThumbnail(p.optJSONObject("thumbnail"))
            out.add(YtmTrackItem(vid, title, artist, "YouTube Music", parseDurationMs(durStr), durStr, thumb))
        }
        return out.distinctBy { it.videoId }
    }

    fun resolveStreamUrl(videoId: String): String? {
        val clients = listOf(
            Triple(
                "ANDROID_VR",
                "1.56.21",
                "com.google.android.apps.youtube.vr.oculus/1.56.21 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)"
            ),
            Triple(
                "IOS",
                "19.29.1",
                "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
            )
        )

        for ((cName, cVer, ua) in clients) {
            runCatching {
                val clientObj = JSONObject()
                    .put("clientName", cName)
                    .put("clientVersion", cVer)
                    .put("hl", "id")
                    .put("gl", "ID")
                if (cName == "IOS") {
                    clientObj.put("deviceMake", "Apple").put("deviceModel", "iPhone16,2").put("osName", "iPhone").put("osVersion", "17.5.1.21F90")
                } else {
                    clientObj.put("androidSdkVersion", 32).put("osName", "Android").put("osVersion", "12")
                }
                val body = JSONObject()
                    .put("videoId", videoId)
                    .put("contentCheckOk", true)
                    .put("racyCheckOk", true)
                    .put("context", JSONObject().put("client", clientObj))

                val json = postJson(
                    "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    body,
                    mapOf("User-Agent" to ua)
                )
                val adaptive = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@runCatching
                var bestUrl: String? = null
                var bestBitrate = 0
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.optJSONObject(i) ?: continue
                    val mime = fmt.optString("mimeType")
                    val url = fmt.optString("url")
                    if (mime.startsWith("audio/") && url.isNotBlank()) {
                        val br = fmt.optInt("bitrate", 0)
                        val score = if (mime.contains("mp4a")) br + 50_000 else br
                        if (score > bestBitrate) {
                            bestBitrate = score
                            bestUrl = url
                        }
                    }
                }
                if (!bestUrl.isNullOrBlank()) return bestUrl
            }
        }
        return null
    }

    private fun parseListItem(r: JSONObject): YtmTrackItem? {
        val flexCols = r.optJSONArray("flexColumns") ?: return null
        val col0 = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        val title = cleanDisplayTitle(extractText(col0))
        if (title.isBlank()) return null

        val subtitleParts = mutableListOf<String>()
        for (i in 1 until flexCols.length()) {
            val t = extractText(flexCols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
            if (t.isNotBlank()) subtitleParts.add(t)
        }
        val splitSub = subtitleParts.joinToString(" • ").split("•").map { it.trim() }.filter { it.isNotBlank() }
        val artist = splitSub.getOrNull(0)?.replace(Regex("(?i)\\s*-\\s*topic$"), "")?.trim() ?: "YouTube Music"
        val album = splitSub.getOrNull(1)?.takeUnless { it.matches(Regex("^\\d+([.,]\\d+)?\\s*(rb|jt|m|k|x)?\\s*(pemutaran|plays|ditonton|views).*$", RegexOption.IGNORE_CASE)) } ?: "YouTube Music"

        var videoId = r.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
        if (videoId.isBlank()) videoId = findFirst(r, "watchEndpoint")?.optString("videoId").orEmpty()
        if (videoId.isBlank()) return null

        val thumb = extractBestThumbnail(r.optJSONObject("thumbnail"))
        val fixed = findFirst(r, "musicResponsiveListItemFixedColumnRenderer")
        val durText = extractText(fixed?.optJSONObject("text")).replace(".", ":")

        return YtmTrackItem(videoId, title, artist, album, parseDurationMs(durText), durText, thumb)
    }

    private fun parseDurationMs(dur: String): Long {
        val parts = dur.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun cleanDisplayTitle(raw: String): String {
        return raw
            .replace(Regex("(?i)\\s*[\\(\\[]\\s*(official|hd|4k|music|lyric|lyrics|audio|video|visualizer|mv)[^\\)\\]]*[\\)\\]]"), "")
            .replace(Regex("(?i)\\s*-\\s*(official|lyric|lyrics|audio|video|visualizer|topic).*$"), "")
            .trim()
            .ifBlank { raw.trim() }
    }

    private fun findAll(obj: Any?, key: String, out: MutableList<JSONObject> = mutableListOf()): List<JSONObject> {
        when (obj) {
            is JSONObject -> {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.opt(k)
                    if (k == key && v is JSONObject) out.add(v)
                    else if (k == key && v is JSONArray) {
                        for (i in 0 until v.length()) v.optJSONObject(i)?.let { out.add(it) }
                    }
                    findAll(v, key, out)
                }
            }
            is JSONArray -> for (i in 0 until obj.length()) findAll(obj.opt(i), key, out)
        }
        return out
    }

    private fun findFirst(obj: Any?, key: String): JSONObject? = findAll(obj, key).firstOrNull()

    private fun extractText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    private fun extractBestThumbnail(obj: Any?): String {
        val thumbs = mutableListOf<JSONObject>()
        findAll(obj, "thumbnails", thumbs)
        var bestUrl = ""
        var maxW = 0
        for (t in thumbs) {
            val w = t.optInt("width", 0)
            val u = t.optString("url")
            if (u.isNotBlank() && w >= maxW) { maxW = w; bestUrl = u }
        }
        return if (bestUrl.contains("googleusercontent.com")) {
            bestUrl.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
        } else bestUrl
    }
}
