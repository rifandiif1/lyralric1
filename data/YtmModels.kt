package com.example.lyralric.data.ytm

data class YtmTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String,
    val duration: String = "",
    val playlistId: String? = null
)

data class YtmSection(
    val title: String,
    val items: List<YtmTrack>
)
