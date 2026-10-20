# Lyra Player — Advanced local music player

Lyra Player is an Android local music player with an LRC lyrics workspace, built with Kotlin, Jetpack Compose and AndroidX Media3. The project is prepared for building through GitHub Actions; it has **not** been compiled or device-tested in this environment.

## Features in this source revision

### Player and library
- Netflix-inspired **Play Now** screen with album artwork when Android's MediaStore exposes it, title, artist, album, progress, elapsed/remaining time, favorite, previous/play/pause/next, shuffle and repeat.
- Animated visualizer bars that animate in time with playback state and position. This is a visual animation, not an FFT/spectrum capture from the audio output.
- Home hero/mini-player, local MediaStore audio scanning, optional user-selected SAF folder, search by title/artist/album/folder, recent history, play counts and favorites.
- Local playlists, queue display, sleep timer, dark/light theme, and headset/audio-focus handling through Media3.
- Background playback via `MediaSessionService` and system media notification/session controls.

### Lyrics
- LRCLIB lookup, LRC parsing and display, import/export LRC, timestamp insertion and timestamp offset adjustment.
- Lyrics widget refreshes with the current timed lyric line.

### Audio and collection tools
- Native Android Equalizer and BassBoost controls tied to the current playback audio session. Effect availability and supported bands depend on the phone, ROM and audio output; the panel gracefully reports when no session is active.
- Smart-shuffle option that favors less-played tracks and penalizes recently played tracks when starting a shuffled selection.
- JSON backup/restore for favorites, play history, play counts, playlist definitions, theme and smart-shuffle setting. Audio files themselves are not copied; imported track IDs work when they match the same local library.
- M3U export for all scanned songs or the selected playlist. Android `content://` URIs are not portable across devices, so this is most useful as a same-device playlist reference unless paths are converted externally.
- User-selected music folder permission is persisted where Android allows it.

## Known limitations / honest notes

- The visualizer is playback-synchronized animation, not true audio spectrum analysis. A true spectrum capture needs Android `Visualizer` permission/session testing and may be blocked by device policy.
- Album artwork is loaded from Android's album-art content URI; some files, SAF-only sources and devices may not expose embedded art, in which case the designed fallback is shown.
- Equalizer and bass boost effects are OEM-dependent and may not affect Bluetooth/DSP routes or all output paths.
- Backup does not copy audio files or embed files into the archive. Playlist IDs may need rescan/relinking after moving the music library.
- M3U import, crossfade controls, ReplayGain/loudness normalization, cloud sync, Android Auto, metadata writing, duplicate scanning and full accessibility QA are not claimed as implemented.
- Run GitHub Actions and test on a real Android device before release. This source has not been verified by a successful local Android build in this environment.

## Build APK on GitHub

1. Create or open a GitHub repository and upload/push the **contents** of the `lyralric-main` folder to the repository root (so `settings.gradle.kts` and `.github/workflows/build-apk.yml` are at the root).
2. Open **Actions** in GitHub and select **Build Lyra Player APK**.
3. Click **Run workflow** (or push to `main`/`master`).
4. Wait for the job to finish. Open the completed run and download the **LyraPlayer-debug-apk** artifact.
5. Extract the ZIP artifact and install the APK on your Android device. Android may ask you to allow installation from the browser/file manager.

The workflow builds a **debug APK**, which is suitable for personal testing. For Play Store distribution, configure a release signing key and a separate signed release workflow. Never commit a signing key or passwords to the repository.


### Live Audio Spectrum (FFT)

The Play Now visualizer uses Android `android.media.audiofx.Visualizer` attached to the Media3 player's audio session and renders 48 logarithmically spaced FFT bands. On first use, tap **Aktifkan analisis spektrum audio** to grant Android's `RECORD_AUDIO` permission. The capture is limited to the app's active playback session; some OEMs/audio routes may block capture, in which case the UI reports that the session is unsupported. The effect is released when playback pauses or the screen leaves the active composition.


### Automatic lyrics lookup (local-first)

- Automatically looks up lyrics whenever the current track title or artist changes.
- Checks the on-device lyrics cache first; the cache is capped at 150 track entries.
- If there is no local entry, searches LRCLIB over HTTPS on a background IO dispatcher and prefers timestamped/synced lyrics when available.
- Associates imported and manually selected lyrics with the current track so they are reused locally.
- Clears stale lyrics when switching to a track with no matching local entry, preventing the previous song's words from being shown while the new lookup runs.
- Includes a Settings toggle for automatic lookup. Manual search and LRC import/export remain available. An internet connection is required for first-time online lookup, and no service can guarantee lyrics exist for every song.


## Perbaikan dan tambahan versi 1.2.0

- Menambahkan deklarasi izin `RECORD_AUDIO` yang diperlukan Android untuk menangkap FFT.
- Visualizer sekarang memiliki animasi fallback ketika izin belum diberikan atau perangkat menolak akses sesi FFT; FFT asli tetap memerlukan izin dan dukungan perangkat.
- Area Play Now menggunakan sampul dengan crop yang lebih sesuai dan menyediakan tombol pensil untuk mengedit judul, artis, dan album pada sesi/tampilan Lyra.
- Editor lirik menampilkan daftar baris dan memungkinkan teks satu baris diubah sambil mempertahankan cap waktunya; simpan untuk memperbarui cache lirik.
- Nomor versi aplikasi dinaikkan ke 1.2.0.

**Batasan metadata:** editor keterangan di versi ini mengubah metadata sesi/tampilan Lyra, bukan tag permanen di berkas audio. Lirik tetap dapat disimpan sebagai cache lokal dan diekspor sebagai `.lrc`; penulisan tag lirik tertanam ke MP3/FLAC belum diterapkan. Perlu build dan pengujian di GitHub Actions serta perangkat Android sebelum digunakan.
