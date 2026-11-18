# Morph v1.1.0

**English** · [Bahasa Indonesia](#bahasa-indonesia)

## New
- **Choose where files are saved** (closes #1): pick any folder, optionally with one subfolder per source file. If the folder is removed or access is revoked, Morph falls back to `Downloads/Morph`.
- **Video resolution and frame rate**: 1080p / 720p / 480p and 60 / 30 / 24 fps for video-to-video conversions. Smaller videos are never upscaled.
- **TXT → EPUB**: plain text becomes an e-book with chapters (headings like "Chapter 1" / "Bab 1" are detected).
- **PDF compression report**: the result shows the size before and after. A PDF that can't get smaller is kept as it is, and Morph says so.
- **Mixed images into one PDF**: photos with different extensions (e.g. WhatsApp's `.jpg` and `.jpeg`) can go into a single PDF.

## Faster and better video
- **New H.264 engine** (x264, built into the app): when a phone's hardware encoder ignores the requested quality, Morph switches to x264 automatically and remembers it for that phone. On a Redmi 15, a 31 s 1080p clip on High: about 44 s, at the full requested quality.
- **Audio is kept as it is** when the target format can hold it: no quality loss and a faster finish (MP4 → M4A on High is almost instant).
- **WebM now uses VP8**, which plays on every Android version. Both VP9 encoders available to Morph produced broken files (the end of the video or the whole video did not play).
- **Images → PDF is about 5× smaller** (30 photos: 13.5 MB before, about the size of the photos now).

## Fixes
- Android 15: the app no longer closes when a conversion starts.
- GIF, HEIC/HEIF and ICO images now convert (they always failed before). TIFF is no longer offered, because Android cannot read it.
- OGG works for mono and voice recordings (it failed before).
- "Compress" (same format) is only offered where it works: MP4, MKV, MOV, WebM, AVI.
- Low and GIF no longer enlarge small videos.
- Videos with non-square pixels (DVD, HDV) keep their correct shape.
- A text file with one damaged character no longer turns the whole e-book into garbled text.
- Instant mode tells you why it re-encoded (for example a portrait video into MKV).
- Android 8–9: a clear way to the storage permission after "Don't ask again". Android 13: sharing files into Morph works. Long queues stop cleanly at Android's 6-hour limit.
- Ready for phones with 16 KB memory pages.

## Requirements
- Android 8.0 or newer, **64-bit phones only** (FFmpeg is 64-bit only). 32-bit phones now get a clear "not compatible" message at install instead of failing later.

## Tested
- 563 automatic conversions (all passing) on a Redmi 15 (Android 15): every format pair the app offers, many codecs and containers, rotated, odd-sized, anamorphic and variable-frame-rate videos, cancelling mid-way, damaged files. Every video and audio result was fully decoded and frame-counted.
- 65 unit tests, including a full format-matrix and fuzz stress suite.

## Licenses
- Morph is GPL-3.0-or-later. New: x264 (GPL-2.0-or-later), listed in the app and in `NOTICE.md`.

---

## Bahasa Indonesia

## Baru
- **Pilih folder penyimpanan** (menutup #1): simpan ke folder mana pun, bisa dengan subfolder per file sumber. Kalau folder dihapus atau izinnya dicabut, Morph kembali ke `Downloads/Morph`.
- **Resolusi dan frame rate video**: 1080p / 720p / 480p dan 60 / 30 / 24 fps untuk konversi video ke video. Video yang lebih kecil tidak pernah diperbesar.
- **TXT → EPUB**: teks biasa jadi e-book berbab (judul seperti "Chapter 1" / "Bab 1" dikenali).
- **Laporan kompres PDF**: hasil menampilkan ukuran sebelum dan sesudah. PDF yang tidak bisa lebih kecil tetap dipakai apa adanya, dan Morph memberi tahu.
- **Gambar campuran ke satu PDF**: foto dengan ekstensi berbeda (mis. `.jpg` dan `.jpeg` dari WhatsApp) bisa digabung jadi satu PDF.

## Video lebih cepat dan lebih baik
- **Mesin H.264 baru** (x264, tertanam di aplikasi): kalau encoder hardware HP mengabaikan kualitas yang diminta, Morph otomatis pindah ke x264 dan mengingatnya untuk HP itu. Di Redmi 15, klip 1080p 31 detik dengan High: sekitar 44 detik, dengan kualitas penuh.
- **Audio dipertahankan apa adanya** bila format tujuan bisa menampungnya: kualitas tidak turun dan selesai lebih cepat (MP4 → M4A di High hampir instan).
- **WebM sekarang memakai VP8**, yang bisa diputar di semua versi Android. Kedua encoder VP9 yang tersedia menghasilkan file rusak (akhir video atau seluruh video tidak bisa diputar).
- **Gambar → PDF sekitar 5× lebih kecil** (30 foto: dulu 13,5 MB, sekarang kira-kira seukuran fotonya).

## Perbaikan
- Android 15: aplikasi tidak lagi tertutup saat konversi dimulai.
- Gambar GIF, HEIC/HEIF, dan ICO sekarang bisa dikonversi (dulu selalu gagal). TIFF tidak lagi ditawarkan karena Android tidak bisa membacanya.
- OGG berfungsi untuk rekaman mono dan suara (dulu gagal).
- "Kompres" (format sama) hanya ditawarkan bila memang berfungsi: MP4, MKV, MOV, WebM, AVI.
- Low dan GIF tidak lagi memperbesar video kecil.
- Video dengan piksel tidak persegi (DVD, HDV) tetap berbentuk benar.
- File teks dengan satu karakter rusak tidak lagi membuat seluruh e-book jadi teks kacau.
- Mode Instan menjelaskan kenapa video di-encode ulang (misalnya video potret ke MKV).
- Android 8–9: jalan yang jelas ke izin penyimpanan setelah "Jangan tanya lagi". Android 13: berbagi file ke Morph berfungsi. Antrean panjang berhenti dengan rapi di batas 6 jam Android.
- Siap untuk HP dengan memory page 16 KB.

## Kebutuhan
- Android 8.0 atau lebih baru, **hanya HP 64-bit** (FFmpeg hanya 64-bit). HP 32-bit sekarang mendapat pesan "tidak kompatibel" yang jelas saat instal, bukan gagal belakangan.

## Diuji
- 563 konversi otomatis (semua lulus) di Redmi 15 (Android 15): semua pasangan format yang ditawarkan, banyak codec dan kontainer, video berotasi, berukuran ganjil, anamorfik, dan frame rate variabel, pembatalan di tengah jalan, file rusak. Setiap hasil video dan audio didekode penuh dan dihitung frame-nya.
- 65 unit test, termasuk stress test matriks format dan fuzzing.

## Lisensi
- Morph berlisensi GPL-3.0-or-later. Baru: x264 (GPL-2.0-or-later), tercantum di aplikasi dan `NOTICE.md`.
