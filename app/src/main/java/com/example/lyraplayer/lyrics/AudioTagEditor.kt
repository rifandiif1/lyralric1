package com.example.lyraplayer.lyrics

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Writes the lyrics field into supported audio tags (for example MP3/FLAC/OGG). */
object AudioTagEditor {
    fun embedLyrics(context: Context, uri: Uri, lyrics: String) {
        require(lyrics.isNotBlank()) { "Isi lirik masih kosong" }
        val resolver = context.contentResolver
        var displayName = ""
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) displayName = cursor.getString(0).orEmpty()
        }
        val ext = displayName.substringAfterLast('.', "mp3")
            .lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp3" }
        val temp = File.createTempFile("lyra-tags-", ".$ext", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                ?: error("Tidak bisa membaca file audio")
            val audio = AudioFileIO.read(temp)
            val tag = audio.tagOrCreateAndSetDefault
            tag.setField(FieldKey.LYRICS, lyrics)
            AudioFileIO.write(audio)
            resolver.openOutputStream(uri, "wt")?.use { output -> temp.inputStream().use { input -> input.copyTo(output) } }
                ?: error("File audio tidak mengizinkan penulisan. Pilih file melalui folder yang bisa ditulis.")
        } finally {
            temp.delete()
        }
    }
}
