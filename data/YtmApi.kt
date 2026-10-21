package com.example.lyralric.data.ytm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object YtmApi {
    private const val YTM_BASE = "https://music.youtube.com/youtubei/v1"
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Parameter filter pencarian dari server.js
    const val FILTER_SONGS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    const val FILTER_VIDEOS = "EgWKAQIQAWoMEA4QChADEAQQCRAF"

    // Request ke InnerTube dengan konteks WEB_REMIX (untuk Search, Home, Queue)
    private fun postWebRemix(endpoint: String, bodyObj: JSONObject): JSONObject {
        val context = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20240101.00.00")
                put("hl", "id")
                put("gl", "ID")
            })
        }
        bodyObj.put("context", context)

        val request = Request.Builder()
            .url("$YTM_BASE/$endpoint?prettyPrint=false")
            .addHeader("Origin", "https://music.youtube.com")
            .addHeader("Referer", "https://music.youtube.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            .post(bodyObj.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("YTM Error: ${response.code}")
            return JSONObject(response.body?.string() ?: "{}")
        }
    }

    // 1. SEARCH LAGU (Konversi dari /api/search di server.js)
    suspend fun searchSongs(query: String, filter: String = FILTER_SONGS): List<YtmTrack> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("query", query)
            if (filter.isNotEmpty()) put("params", filter)
        }
        val root = postWebRemix("search", body)
        val items = mutableListOf<YtmTrack>()

        val shelves = findAll(root, "musicShelfRenderer")
        for (shelf in shelves) {
            val contents = shelf.optJSONArray("contents") ?: continue
            for (i in 0 until contents.length()) {
                val itemObj = contents.optJSONObject(i)?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                parseListItem(itemObj)?.let { items.add(it) }
            }
        }
        items
    }

    // 2. HOME FEED (Konversi dari /api/home di server.js)
    suspend fun getHomeSections(): List<YtmSection> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("browseId", "FEmusic_home") }
        val root = postWebRemix("browse", body)
        val sections = mutableListOf<YtmSection>()

        val sectionList = findFirst(root, "sectionListRenderer")
        val contents = sectionList?.optJSONArray("contents") ?: return@withContext emptyList()

        for (i in 0 until contents.length()) {
            val sec = contents.optJSONObject(i) ?: continue
            val carousel = sec.optJSONObject("musicCarouselShelfRenderer")
            if (carousel != null) {
                val headerTitle = extractText(findFirst(carousel.optJSONObject("header"), "title"))
                val carContents = carousel.optJSONArray("contents") ?: continue
                val tracks = mutableListOf<YtmTrack>()
                for (j in 0 until carContents.length()) {
                    val c = carContents.optJSONObject(j) ?: continue
                    c.optJSONObject("musicResponsiveListItemRenderer")?.let {
                        parseListItem(it)?.let { track -> tracks.add(track) }
                    }
                    c.optJSONObject("musicTwoRowItemRenderer")?.let {
                        parseTwoRow(it)?.let { track -> tracks.add(track) }
                    }
                }
                if (tracks.isNotEmpty()) sections.add(YtmSection(headerTitle, tracks))
            }
        }
        sections
    }

    // 3. QUEUE / RADIO & LIRIK BROWSE ID (Konversi dari /api/next di server.js)
    suspend fun getRadioQueue(videoId: String): Pair<List<YtmTrack>, String?> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
        }
        val root = postWebRemix("next", body)
        val queue = mutableListOf<YtmTrack>()

        val panels = findAll(root, "playlistPanelVideoRenderer")
        for (p in panels) {
            val vid = p.optString("videoId")
            if (vid.isEmpty()) continue
            val title = cleanDisplayTitle(extractText(p.optJSONObject("title")))
            val artist = extractText(p.optJSONObject("shortBylineText") ?: p.optJSONObject("longBylineText"))
            val duration = extractText(p.optJSONObject("lengthText"))
            val thumb = extractBestThumbnail(p.optJSONObject("thumbnail"))
            queue.add(YtmTrack(vid, title, artist, thumb, duration))
        }

        // Ambil browseId untuk lirik bawaan YouTube Music jika ada
        var lyricsBrowseId: String? = null
        for (tab in findAll(root, "tabRenderer")) {
            val id = tab.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId") ?: ""
            if (id.startsWith("MPLYt")) lyricsBrowseId = id
        }

        Pair(queue, lyricsBrowseId)
    }

    // 4. EKSTRAK DIRECT AUDIO URL UNTUK EXOPLAYER
    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        // Menggunakan client IOS atau ANDROID_VR untuk mendapatkan direct URL tanpa cipher signature
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "IOS")
                    put("clientVersion", "19.29.1")
                    put("deviceMake", "Apple")
                    put("deviceModel", "iPhone16,2")
                    put("osName", "iPhone")
                    put("osVersion", "17.5.1.21F90")
                    put("hl", "id")
                    put("gl", "ID")
                })
            })
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .addHeader("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body?.string() ?: "{}")
            val adaptiveFormats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@withContext null

            var bestAudioUrl: String? = null
            var bestBitrate = 0

            for (i in 0 until adaptiveFormats.length()) {
                val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                val mime = fmt.optString("mimeType")
                if (mime.startsWith("audio/")) {
                    val url = fmt.optString("url")
                    val bitrate = fmt.optInt("bitrate", 0)
                    // Prioritaskan mp4a (m4a) atau opus dengan bitrate tertinggi
                    if (url.isNotEmpty() && bitrate > bestBitrate) {
                        bestBitrate = bitrate
                        bestAudioUrl = url
                    }
                }
            }
            bestAudioUrl
        }
    }

    /* ---------------- Helper JSON (Porting dari server.js) ---------------- */

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
            is JSONArray -> {
                for (i in 0 until obj.length()) findAll(obj.opt(i), key, out)
            }
        }
        return out
    }

    private fun findFirst(obj: Any?, key: String): JSONObject? = findAll(obj, key).firstOrNull()

    private fun extractText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString()
    }

    private fun extractBestThumbnail(obj: Any?): String {
        val thumbs = mutableListOf<JSONObject>()
        findAll(obj, "thumbnails", thumbs)
        var bestUrl = ""
        var maxW = 0
        for (t in thumbs) {
            val w = t.optInt("width", 0)
            val url = t.optString("url")
            if (url.isNotEmpty() && w >= maxW) {
                maxW = w
                bestUrl = url
            }
        }
        return if (bestUrl.contains("googleusercontent.com")) {
            bestUrl.replace(Regex("=w\\d+-h\\d+.*$"), "=w544-h544-l90-rj")
        } else bestUrl
    }

    private fun parseListItem(r: JSONObject): YtmTrack? {
        val flexCols = r.optJSONArray("flexColumns") ?: return null
        val col0 = flexCols.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        val title = cleanDisplayTitle(extractText(col0))
        if (title.isEmpty()) return null

        val subtitleParts = mutableListOf<String>()
        for (i in 1 until flexCols.length()) {
            val colText = extractText(flexCols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))
            if (colText.isNotEmpty()) subtitleParts.add(colText)
        }
        val artist = subtitleParts.firstOrNull()?.split("•")?.firstOrNull()?.trim() ?: ""

        var videoId = r.optJSONObject("playlistItemData")?.optString("videoId") ?: ""
        if (videoId.isEmpty()) {
            videoId = findFirst(r, "watchEndpoint")?.optString("videoId") ?: ""
        }
        if (videoId.isEmpty()) return null

        val thumb = extractBestThumbnail(r.optJSONObject("thumbnail"))
        val fixedCol = findFirst(r, "musicResponsiveListItemFixedColumnRenderer")
        val duration = extractText(fixedCol?.optJSONObject("text")).replace(".", ":")

        return YtmTrack(videoId, title, artist, thumb, duration)
    }

    private fun parseTwoRow(r: JSONObject): YtmTrack? {
        val videoId = findFirst(r.optJSONObject("navigationEndpoint"), "watchEndpoint")?.optString("videoId") ?: return null
        val title = cleanDisplayTitle(extractText(r.optJSONObject("title")))
        val artist = extractText(r.optJSONObject("subtitle"))
        val thumb = extractBestThumbnail(r.optJSONObject("thumbnailRenderer"))
        return YtmTrack(videoId, title, artist, thumb)
    }

    // Pembersih judul lagu seperti fungsi displayTitle() di server.js
    fun cleanDisplayTitle(raw: String): String {
        return raw
            .replace(Regex("\\s*[\\(\\[]\\s*(official|hd|4k|music|lyric|lyrics|audio|video|visualizer|mv)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(official|lyric|lyrics|audio|video|visualizer|topic).*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { raw }
    }
}
