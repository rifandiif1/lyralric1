# Lyra Player — status fitur

## Ditambahkan/diperbaiki pada pembaruan ini
- Animasi visualizer fallback bergerak terus saat lagu diputar dan FFT tidak tersedia, sesi audio belum siap, atau izin belum diberikan.
- FFT tetap digunakan jika efek `Visualizer` tersedia dan berhasil diaktifkan.
- Editor lirik per baris sekarang memakai indeks baris asli sehingga baris lirik yang teksnya sama tidak salah diedit.
- Tombol **Tanamkan lirik ke file audio** menulis lirik ke field tag `LYRICS` menggunakan jaudiotagger untuk format yang didukung.
- Perbaikan baris izin AndroidManifest yang sebelumnya mengandung karakter `\\n` literal.
- Keterangan metadata di UI diperjelas agar tidak mengklaim tag judul/artis/album sudah ditulis ke file audio.

## Sudah ada di proyek
- Pemutar audio Media3, perpustakaan musik, pencarian, antrean, shuffle/repeat.
- Playlist, favorit, riwayat/penghitungan putar, timer tidur.
- Pencarian lirik LRCLIB, cache lirik lokal, impor dan ekspor `.lrc`.
- Editor teks lirik, tambah cap waktu dari posisi pemutaran, geser semua cap waktu ±0,5 detik.
- Widget lirik, pengaturan tema, equalizer/bass boost bergantung dukungan perangkat.
- Backup/restore sebagian pengaturan, playlist, favorit, dan riwayat.

## Belum tersedia atau masih perlu pengerjaan/pengujian
1. **Menulis judul/artis/album ke tag audio asli** — edit metadata saat ini hanya memperbarui sesi/UI Lyra.
2. **Penyematan lirik belum universal** — bergantung format/tag dan izin tulis URI. MP3/FLAC/OGG lebih mungkin didukung; M4A/AAC/WAV dan penyedia berkas tertentu bisa gagal.
3. **Dialog izin Android untuk mengedit berkas MediaStore yang dilindungi** — belum ada alur `RecoverableSecurityException`/konfirmasi pengguna; jika gagal, ekspor LRC tetap menjadi alternatif.
4. **Sinkronisasi kata-per-kata (karaoke)** — editor hanya menyunting baris, bukan tiap kata.
5. **Editor cap waktu per baris dengan tombol sinkronisasi maju/mundur khusus** — saat ini cap waktu dibuat dari posisi playback dan seluruh LRC bisa digeser.
6. **Pengambilan sampul dan pengeditan artwork/genre/nomor track/tahun/komposer** — belum tersedia.
7. **Penyimpanan metadata override per lagu setelah pemindaian ulang atau restart** — belum dijamin konsisten.
8. **Pengujian build APK di GitHub Actions dan pengujian perangkat nyata** — belum dapat dinyatakan lulus hanya dari perubahan sumber.
9. **Visualizer FFT lintas semua perangkat** — Android/OEM dapat menolak efek audio; fallback tersedia, tetapi spektrum real-time perlu diuji di perangkat.
10. **Lirik offline lengkap dan pengunduhan otomatis batch** — cache tersedia, namun belum ada pengelola unduhan lirik massal.

## Catatan build
Proyek membutuhkan koneksi Gradle untuk mengambil dependensi, termasuk `net.jthink:jaudiotagger:3.0.1`. Jalankan workflow GitHub Actions dan periksa log build sebelum mendistribusikan APK.
