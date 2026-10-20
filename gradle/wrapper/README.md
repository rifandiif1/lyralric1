# Lyra Player 1.1.0

A native Android local-music player built with Kotlin, Jetpack Compose, AndroidX Media3, and the public LRCLIB API.

## Included features

- Play a local audio file selected through Android's Storage Access Framework; the selected URI permission and track title are remembered for later launches.
- Background playback using ExoPlayer and `MediaSessionService`, with Android system media controls and media notification.
- Playback controls: play/pause, seek bar, and 10-second forward/backward seeking.
- Search LRCLIB by track title and optional artist, with readable handling of HTTP/network errors.
- Select timed LRC lyrics or plain-text lyrics from search results.
- Import and export `.lrc` files; edit lyric text directly.
- Add a timestamp at the current playback position and shift every timestamp by 500 ms earlier/later.
- Preview timed lyrics and show the current lyric in the app.
- Home-screen lyric widget, refreshed when the current line changes, which opens the app when tapped.
- Remember the latest lyrics and selected track URI between launches.
- Dark Material 3 interface.

## Build the APK

A ZIP is a project archive, not an Android application package. A website that converts a ZIP's file extension cannot compile Android source code into an APK. Use Android Studio to build this project:

1. Extract the ZIP.
2. Open the `LyraPlayer` directory in Android Studio (the directory containing `settings.gradle.kts`).
3. Use JDK 17 and install Android SDK platform 35 if Android Studio requests it.
4. Let Gradle sync finish, then choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. The debug APK is normally written to `app/build/outputs/apk/debug/app-debug.apk`.
6. For sharing outside your own device, choose **Build → Generate Signed Bundle / APK** and create your own signing key.

## Platform notes

- LRCLIB requires an internet connection. Lyrics are retrieved from LRCLIB; this app does not bundle a lyric catalogue.
- The widget updates when the playback service observes a changed lyric line. Android launchers can throttle widget updates, so refresh timing may vary by device.
- Android's lock screen is controlled by the OS and device manufacturer. MediaSession provides track metadata and media controls, but Android does not offer a universal API for forcing a custom, continuously scrolling full-lyrics panel on every lock screen. The home-screen widget is the reliable dedicated lyrics surface.
- This environment does not include an Android SDK/Gradle installation, so the package could not be built or device-tested here. Build with Android Studio before installing it on a phone.

## Project structure

- `app/src/main/java/com/example/lyraplayer/MainActivity.kt` — player interface, LRCLIB search, lyric editor, file pickers.
- `lyrics/LrclibClient.kt` — LRCLIB API client.
- `lyrics/LrcParser.kt` — timestamp parsing, current-line lookup, timestamp shifting, and plain-text fallback.
- `playback/PlaybackService.kt` — background playback and lyric-widget refresh.
- `widget/LyricsWidgetProvider.kt` — Android home-screen widget.
