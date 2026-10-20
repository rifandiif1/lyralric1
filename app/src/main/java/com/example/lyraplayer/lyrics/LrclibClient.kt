package com.example.lyraplayer.lyrics

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small synchronous client; callers must run it on Dispatchers.IO. */
data class LrcResult(
    val name: String,
    val artist: String,
    val album: String?,
    val syncedLyrics: String?,
    val plainLyrics: String?
)

object LrclibClient {
    private fun enc(value: String): String = URLEncoder.encode(value.trim(), Charsets.UTF_8.name())

    fun search(track: String, artist: String = ""): List<LrcResult> {
        require(track.isNotBlank()) { "Masukkan judul lagu terlebih dahulu." }
        val query = buildString {
            append("https://lrclib.net/api/search?track_name=").append(enc(track))
            if (artist.isNotBlank()) append("&artist_name=").append(enc(artist))
        }
        val body = request(query)
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            resultFrom(o)
        }
    }

    fun getById(id: Long): LrcResult? {
        if (id <= 0) return null
        val body = request("https://lrclib.net/api/get/$id")
        return resultFrom(JSONObject(body))
    }

    private fun resultFrom(o: JSONObject) = LrcResult(
        name = o.optString("trackName").ifBlank { "Judul tidak diketahui" },
        artist = o.optString("artistName").ifBlank { "Artis tidak diketahui" },
        album = o.optString("albumName").takeIf { it.isNotBlank() && it != "null" },
        syncedLyrics = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
        plainLyrics = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
    )

    private fun request(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "LyraPlayer/1.1 (Android; LRCLIB client)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = when (code) {
                    404 -> "Lirik tidak ditemukan."
                    429 -> "Terlalu banyak permintaan. Coba lagi sebentar."
                    in 500..599 -> "Server LRCLIB sedang bermasalah ($code)."
                    else -> "Permintaan LRCLIB gagal ($code)."
                }
                throw IOException(message)
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}
