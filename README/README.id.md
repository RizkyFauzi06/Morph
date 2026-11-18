<div align="center">

  <img src="../assets/Code_Generated_Image.png" alt="Logo Morph" width="112" />

  <h1>Morph</h1>
  <p><strong>Offline File Converter untuk Android</strong></p>

  <p>
    <a href="../README.md">English</a> &nbsp;|&nbsp; <strong>Bahasa Indonesia</strong>
  </p>

  <p>
    <a href="https://github.com/RizkyFauzi06/Morph/releases"><img src="https://img.shields.io/badge/Download-APK-7952E8?style=flat-square&logo=android&logoColor=white" alt="Download APK" /></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B%2C%2064--bit)-249B69?style=flat-square&logo=android&logoColor=white" alt="Android 8.0+ (64-bit)" />
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <a href="../LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-555555?style=flat-square" alt="Lisensi GPL-3.0" /></a>
  </p>

  <p>
    <a href="#fitur">Fitur</a> &nbsp;&bull;&nbsp;
    <a href="#format-yang-didukung">Format yang Didukung</a> &nbsp;&bull;&nbsp;
    <a href="#instalasi">Instalasi</a> &nbsp;&bull;&nbsp;
    <a href="#cara-penggunaan">Cara Penggunaan</a> &nbsp;&bull;&nbsp;
    <a href="#privasi--izin">Privasi &amp; Izin</a> &nbsp;&bull;&nbsp;
    <a href="#tech-stack--lisensi">Tech Stack &amp; Lisensi</a>
  </p>

</div>

<hr />

<p>
  <strong>Morph</strong> adalah aplikasi konverter file 100% lokal (<em>offline-first</em>) untuk Android yang dibangun dengan Kotlin dan Jetpack Compose. Ubah format video, audio, gambar (raster maupun vektor), dokumen PDF, hingga e-book langsung di HP Anda—tanpa unggahan cloud, tanpa akun, dan tanpa pelacakan data.
</p>

<h2 id="fitur">Fitur Utama</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>100% Offline &amp; Privat</h3>
      <p>Seluruh proses konversi, ekstraksi suara, dan pengolahan dokumen berjalan sepenuhnya di dalam HP Anda—file tidak pernah dikirim ke server mana pun. Akses internet hanya dipakai seperlunya untuk mengecek rilis versi baru di GitHub (bisa dimatikan kapan saja).</p>
    </td>
    <td width="50%" valign="top">
      <h3>Batch Conversion (Konversi Massal)</h3>
      <p>Pilih banyak file dengan berbagai format sekaligus dalam satu waktu. Morph otomatis mengelompokkannya berdasarkan format asal, sehingga Anda bisa menentukan format tujuan dan preset kualitas yang berbeda untuk tiap kelompok.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>Share to Morph</h3>
      <p>Kirim satu atau banyak file langsung dari Galeri, File Manager, atau browser lewat menu <strong>Bagikan (Share)</strong> bawaan Android tanpa harus membuka aplikasi Morph terlebih dahulu—lengkap dengan opsi tutup otomatis (<em>Auto-Close</em>) setelah selesai.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Antrean Latar Belakang &amp; Notifikasi</h3>
      <p>Didukung antrean berurutan dan <em>Foreground Service</em> Android. Proses konversi tetap berjalan aman di latar belakang meski Anda berpindah aplikasi atau mematikan layar, lengkap dengan notifikasi progres secara <em>real-time</em>.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>Bebas Pilih Folder Simpan <sup>v1.1.0</sup></h3>
      <p>Simpan hasil konversi ke folder mana saja (memori internal maupun kartu SD) lewat pemilih folder Android, lengkap dengan mode opsional <strong>Subfolder per File</strong> (<code>Buku.pdf</code> &rarr; <code>Buku/</code>). Jika folder dihapus atau izinnya dicabut, penyimpanan otomatis kembali ke <code>Downloads/Morph</code>.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Atur Resolusi &amp; Frame Rate Video <sup>v1.1.0</sup></h3>
      <p>Perkecil ukuran video secara opsional ke <strong>1080p</strong>, <strong>720p</strong>, atau <strong>480p</strong> (proporsi layar tetap terjaga) serta ubah kelancaran gerak (<em>frame rate</em>) ke <strong>60</strong>, <strong>30</strong>, atau <strong>24 fps</strong> sesuai kebutuhan.</p>
    </td>
  </tr>
</table>

<h2 id="tampilan--alur-kerja">Tampilan &amp; Alur Kerja</h2>

<div align="center">
  <table>
    <tr>
      <td width="50%" align="center" valign="top">
        <img src="../assets/Homescreen.jpg" alt="Homescreen & File History" width="260" />
        <br /><br />
        <strong>Homescreen &amp; File History</strong>
        <br />
        <sub>Pilih file dengan cepat, saring berdasarkan kategori (Dokumen, Gambar, Video, Audio), dan kelola riwayat file hasil konversi.</sub>
      </td>
      <td width="50%" align="center" valign="top">
        <img src="../assets/Share%20To%20Convert.jpg" alt="Share to Morph" width="260" />
        <br /><br />
        <strong>Share to Morph</strong>
        <br />
        <sub>Konversi file langsung dari Galeri, File Manager, atau aplikasi lain cukup lewat menu Share bawaan Android.</sub>
      </td>
    </tr>
    <tr>
      <td width="50%" align="center" valign="top">
        <img src="../assets/Direct%20Web%20Convertion.jpg" alt="Direct Web Format Conversion" width="260" />
        <br /><br />
        <strong>Direct Web Format Conversion</strong>
        <br />
        <sub>Kirim gambar langsung dari Google atau browser (<em>"Bagikan gambar"</em> / <em>"Share image"</em>), atau ubah format web (WebP, WebM, SVG) menjadi file standar siap offline tanpa perlu mengunduhnya dulu.</sub>
      </td>
      <td width="50%" align="center" valign="top">
        <img src="../assets/Batch%20Processing.jpg" alt="Batch Conversion" width="260" />
        <br /><br />
        <strong>Batch Conversion</strong>
        <br />
        <sub>Mengelompokkan banyak file terpilih secara otomatis berdasarkan format asalnya, dengan pengaturan format tujuan dan preset kualitas masing-masing.</sub>
      </td>
    </tr>
  </table>
</div>

<h2 id="format-yang-didukung">Format yang Didukung</h2>

<p>
  Secara bawaan, hasil konversi disimpan ke folder <code>Downloads/Morph</code> atau ke lokasi pilihan Anda (<strong>Pengaturan &rarr; Folder Output</strong>), lengkap dengan opsi mempertahankan nama file asli dan mengelompokkan hasil ke dalam subfolder per file.
</p>

<table>
  <thead>
    <tr>
      <th align="left">Kategori</th>
      <th align="left">Format Asal</th>
      <th align="left">Format Tujuan</th>
      <th align="left">Kemampuan &amp; Catatan</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Konversi Video</strong></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code>, <code>AVI</code>, <code>FLV</code>, <code>WMV</code>, <code>3GP</code>, <code>M4V</code>, <code>TS</code>, <code>OGV</code></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code>, <code>AVI</code>, <code>GIF</code></td>
      <td>
        <ul>
          <li>Mode <strong>Instant</strong> (<em>stream copy</em> tanpa kompresi ulang) bila codec asal cocok dengan wadah tujuan; jika tidak, otomatis di-<em>encode</em> ulang.</li>
          <li>Encoding H.264 cepat (memakai akselerasi <em>hardware</em> HP bila menaati target bitrate, atau otomatis memakai mesin <strong>x264</strong> bawaan agar kualitas tetap tajam).</li>
          <li>Opsi resolusi (<strong>1080p / 720p / 480p</strong>) dan <em>frame rate</em> (<strong>60 / 30 / 24 fps</strong>)—video yang sudah kecil tidak pernah dipaksa membesar.</li>
          <li>Encoding <strong>VP8/Opus</strong> untuk WebM agar lancar diputar di semua versi Android, serta ekspor <strong>GIF animasi</strong> dengan proporsi layar terjaga.</li>
          <li>Trek audio yang sudah kompatibel dipertahankan apa adanya. Opsi <em>Kompres</em> (format sama) tersedia untuk <code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WebM</code>, dan <code>AVI</code>.</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td><strong>Ekstrak Audio dari Video</strong></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code>, <code>AVI</code>, <code>FLV</code>, <code>WMV</code>, <code>3GP</code>, <code>M4V</code>, <code>TS</code>, <code>OGV</code></td>
      <td><code>MP3</code>, <code>M4A</code>, <code>WAV</code>, <code>FLAC</code>, <code>OGG</code>, <code>OPUS</code></td>
      <td>Mengambil jalur suara langsung dari file video dengan pilihan preset kualitas. Jika video tidak memiliki trek suara (bisu), aplikasi memberi pesan yang jelas alih-alih menghasilkan file kosong.</td>
    </tr>
    <tr>
      <td><strong>Konversi Audio</strong></td>
      <td><code>MP3</code>, <code>WAV</code>, <code>FLAC</code>, <code>AAC</code>, <code>M4A</code>, <code>OGG</code>, <code>OPUS</code>, <code>WMA</code>, <code>AIFF</code>, <code>AMR</code></td>
      <td><code>MP3</code>, <code>WAV</code>, <code>AAC</code>, <code>FLAC</code>, <code>M4A</code>, <code>OGG</code>, <code>OPUS</code></td>
      <td>
        <ul>
          <li>Mendukung PCM <code>WAV</code> tanpa kompresi, <code>FLAC</code> <em>lossless</em>, <code>MP3</code>/<code>AAC</code> bitrate tinggi, serta <code>OGG</code> Vorbis (termasuk untuk rekaman mono dan suara).</li>
          <li>Mendukung <em>stream copy</em> <strong>Instant</strong> bila codec audio asal sudah sesuai dengan format tujuan (preset <strong>High</strong> juga mempertahankan trek yang cocok apa adanya).</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td><strong>Konversi Gambar</strong></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>JPEG</code>, <code>WEBP</code>, <code>BMP</code>, <code>GIF</code>, <code>HEIC</code>, <code>HEIF</code>, <code>ICO</code></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>WEBP</code>, <code>BMP</code>, <code>PDF</code></td>
      <td>
        <ul>
          <li>Otomatis memberi latar belakang putih saat mengubah gambar transparan ke <code>JPG</code> atau <code>BMP</code>.</li>
          <li>Banyak foto sekaligus (termasuk campuran <code>.jpg</code> dan <code>.jpeg</code>) bisa digabung menjadi satu PDF ringkas (halaman JPEG) atau disimpan terpisah.</li>
          <li><code>GIF</code> mengambil frame pertamanya; <code>HEIC</code>/<code>HEIF</code> membutuhkan Android 9 ke atas; <code>TIFF</code> tidak didukung karena Android tidak memiliki <em>decoder</em> bawaan untuk format ini.</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td><strong>Konversi SVG</strong></td>
      <td><code>SVG</code></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>WEBP</code></td>
      <td>Mengubah grafik vektor menjadi gambar resolusi tinggi menggunakan AndroidSVG dengan mempertahankan transparansi penuh pada <code>PNG</code>.</td>
    </tr>
    <tr>
      <td><strong>Pengolah PDF</strong></td>
      <td><code>PDF</code></td>
      <td><code>JPG</code>, <code>PNG</code>, <code>PDF</code></td>
      <td>
        <ul>
          <li>Mengubah setiap halaman PDF menjadi gambar resolusi tinggi secara terpisah (aman di memori meski halaman berukuran besar).</li>
          <li>Mengompres ukuran PDF dan menampilkan perbandingan ukuran sebelum dan sesudah selesai (misalnya <code>2.4 MB &rarr; 850 KB (-65%)</code>).</li>
        </ul>
      </td>
    </tr>
    <tr>
      <td><strong>EPUB &hArr; TXT</strong></td>
      <td><code>EPUB</code>, <code>TXT</code></td>
      <td><code>TXT</code>, <code>EPUB</code></td>
      <td>
        <ul>
          <li><strong>EPUB &rarr; TXT:</strong> Membaca urutan bab (<em>spine</em> OPF) untuk mengekstrak teks polos yang bersih dari buku digital.</li>
          <li><strong>TXT &rarr; EPUB:</strong> Menyusun file teks biasa menjadi e-book EPUB standar lengkap dengan deteksi bab otomatis (<em>Bab 1</em>, <em>Chapter 2</em>, dst.) dan daftar isi.</li>
        </ul>
      </td>
    </tr>
  </tbody>
</table>

<h3>Preset Kualitas</h3>
<ul>
  <li><strong>High:</strong> Mempertahankan resolusi maksimal dan bitrate asli dengan kompresi seminimal mungkin.</li>
  <li><strong>Medium:</strong> Menyeimbangkan kualitas hasil dengan penghematan ukuran file (~50%).</li>
  <li><strong>Low:</strong> Kompresi tinggi dan penyesuaian skala untuk menghemat ruang penyimpanan secara maksimal.</li>
  <li><strong>Instant:</strong> Memindahkan isi media ke wadah baru (<em>remux</em>) tanpa kompresi ulang—selesai dalam hitungan detik dengan kualitas 100% utuh seperti aslinya.</li>
</ul>

<h2 id="instalasi">Instalasi</h2>

<h3>Opsi 1: Pasang Langsung via APK (Direkomendasikan)</h3>
<p>
  Anda tidak perlu mengunduh atau mengompilasi kode sumber untuk langsung menggunakan Morph:
</p>
<ol>
  <li>Buka halaman <a href="https://github.com/RizkyFauzi06/Morph/releases"><strong>Releases</strong></a> pada repositori ini.</li>
  <li>Unduh file <code>Morph-v*.apk</code> terbaru dari <a href="https://github.com/RizkyFauzi06/Morph/releases/latest">GitHub Releases</a>.</li>
  <li>Buka file APK tersebut di HP Android Anda (minimal <strong>Android 8.0</strong>, arsitektur <strong>64-bit</strong>) dan izinkan instalasi dari browser atau file manager jika diminta. <em>(Catatan: HP dengan Android 32-bit—umumnya Android Go dan beberapa model lama—belum didukung karena mesin FFmpeg hanya menyediakan library 64-bit).</em></li>
</ol>

<h3>Opsi 2: Build dari Source Code</h3>
<p>
  Jika ingin memeriksa atau mengompilasi proyek ini sendiri, siapkan:
</p>
<ul>
  <li><strong>JDK 21</strong></li>
  <li><strong>Android SDK</strong> (cukup Command-Line Tools, misalnya bersama VS Code) <strong>atau Android Studio</strong></li>
</ul>
<p>
  Arahkan Gradle ke folder SDK Anda melalui variabel lingkungan <code>ANDROID_HOME</code> atau file <code>local.properties</code> berisi <code>sdk.dir=/path/ke/Android/sdk</code>, lalu jalankan perintah berikut:
</p>

```bash
git clone [https://github.com/RizkyFauzi06/Morph.git](https://github.com/RizkyFauzi06/Morph.git)
cd Morph

# Kompilasi Debug APK
./gradlew assembleDebug
```

<p>
  File APK hasil kompilasi akan tersedia di <code>app/build/outputs/apk/debug/app-debug.apk</code>.
</p>

<h2 id="cara-penggunaan">Cara Penggunaan</h2>

<h3>1. Konversi Langsung melalui Homescreen</h3>
<ol>
  <li>Buka <strong>Morph</strong> dan ketuk <strong>Pilih File</strong> pada layar <strong>Homescreen</strong>.</li>
  <li>Pilih satu atau beberapa file dari perangkat Anda (video, audio, gambar, format web seperti WebP/WebM/SVG, PDF, atau EPUB).</li>
  <li>Pilih <strong>Format Tujuan</strong> dan <strong>Quality Preset</strong> (<strong>High</strong>, <strong>Medium</strong>, <strong>Low</strong>, atau <em>stream copy</em> lossless <strong>Instant</strong> untuk media yang kompatibel).</li>
  <li><em>(Opsional)</em> Aktifkan sakelar <strong>Gabung menjadi satu PDF</strong> saat memilih beberapa file gambar, atau pilih <strong>Resolusi</strong> dan <strong>Frame Rate</strong> saat mengonversi video ke video.</li>
  <li>Ketuk <strong>Konversi</strong>. Tugas berjalan aman melalui <strong>Background Queue &amp; Notifications</strong>, dan hasil konversi disimpan ke folder output Anda (bawaan: <code>Downloads/Morph</code>).</li>
</ol>

<h3>2. Bagikan dari Aplikasi Lain (Share to Morph)</h3>
<ol>
  <li>Pilih satu atau beberapa file di <strong>Galeri</strong>, <strong>File Manager</strong>, atau ketuk <strong>Bagikan gambar</strong> (<em>Share image</em>) langsung pada gambar di Google/browser seluler.</li>
  <li>Pilih <strong>Morph</strong> dari menu <strong>Share</strong> bawaan Android.</li>
  <li>Pilih format tujuan pada panel <em>bottom sheet</em> yang muncul dan ketuk <strong>Konversi</strong> tanpa meninggalkan aplikasi yang sedang dibuka.</li>
</ol>

<h3>3. Kelola File History</h3>
<ul>
  <li>Filter file hasil konversi berdasarkan kategori di <strong>Homescreen</strong>, atau ketuk item mana pun pada <strong>File History</strong> untuk membuka, membagikan, atau menghapusnya.</li>
  <li>Aktifkan <strong>Tutup Otomatis (Share)</strong> atau <strong>Pertahankan Nama Asli</strong> di <strong>Pengaturan</strong> untuk menyesuaikan alur kerja Anda.</li>
  <li>Pilih <strong>Folder Output</strong> kustom dan aktifkan <strong>Subfolder per File</strong> di <strong>Pengaturan</strong>. File yang tersimpan di sana juga muncul di Riwayat File.</li>
</ul>

<h2 id="privasi--izin">Privasi &amp; Izin</h2>

<p><strong>Offline Engine &amp; Private:</strong> Seluruh konversi file, transcoding media, dan penguraian dokumen berjalan 100% secara lokal di perangkat Anda. Morph tidak memiliki server backend, tidak ada analitik, dan tidak pernah mengunggah file Anda.</p>

<table>
  <thead>
    <tr>
      <th align="left">Izin / Pengaturan Android</th>
      <th align="left">Tujuan</th>
      <th align="center">Status</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>INTERNET</code></td>
      <td>Digunakan <strong>khusus</strong> untuk memeriksa rilis aplikasi baru melalui GitHub API (dapat dimatikan di Pengaturan). Tidak pernah digunakan saat proses konversi file.</td>
      <td align="center">Opsional</td>
    </tr>
    <tr>
      <td><code>POST_NOTIFICATIONS</code></td>
      <td>Menampilkan progres antrean konversi secara <em>real-time</em> dan notifikasi selesai pada Android 13+.</td>
      <td align="center">Opsional</td>
    </tr>
    <tr>
      <td><code>FOREGROUND_SERVICE</code>, <code>FOREGROUND_SERVICE_DATA_SYNC</code> &amp; <code>FOREGROUND_SERVICE_MEDIA_PROCESSING</code></td>
      <td>Menjaga proses konversi lokal tetap berjalan lancar saat Anda berpindah aplikasi atau mengunci layar (tipe media-processing di Android 15+, data-sync sebelumnya).</td>
      <td align="center">Wajib</td>
    </tr>
    <tr>
      <td><code>Unrestricted Battery Usage</code> <em>(Pengaturan Sistem)</em></td>
      <td>Mencegah penghemat baterai Android menghentikan konversi video atau audio berdurasi panjang di latar belakang (diatur manual melalui Info Aplikasi).</td>
      <td align="center">Direkomendasikan</td>
    </tr>
    <tr>
      <td><code>WRITE_EXTERNAL_STORAGE</code> / <code>READ_EXTERNAL_STORAGE</code></td>
      <td>Hanya diminta di Android 8&ndash;10 untuk menyimpan dan menampilkan file di <code>Downloads/Morph</code>. Tidak dipakai di Android 11+.</td>
      <td align="center">Android 8&ndash;10</td>
    </tr>
    <tr>
      <td>Akses folder <em>(Storage Access Framework)</em></td>
      <td>Hanya diberikan untuk folder output kustom yang Anda pilih. Dapat dicabut kapan saja; Morph lalu memakai <code>Downloads/Morph</code>.</td>
      <td align="center">Opsional</td>
    </tr>
  </tbody>
</table>

<h2 id="tech-stack--lisensi">Tech Stack &amp; Lisensi</h2>

<ul>
  <li><strong>Bahasa &amp; UI:</strong> Kotlin, Jetpack Compose, AndroidX, &amp; Kotlin Coroutines (Apache License 2.0).</li>
  <li><strong>Pustaka Inti (Mesin Pihak Ketiga):</strong>
    <ul>
      <li><strong>FFmpegKit Full (8.1.7)</strong> — Pemrosesan audio/video (Lisensi: <strong>GPL-3.0 / LGPL-3.0</strong>).</li>
      <li><strong>x264 (build 165)</strong> — Encoder H.264 software native (NEON) di <code>libmorph-native.so</code> (Lisensi: <strong>GPL-2.0-or-later</strong>).</li>
      <li><strong>AndroidSVG (1.4)</strong> oleh Paul LeBeau — Rendering grafik vektor SVG (Lisensi: <strong>Apache License 2.0</strong>).</li>
      <li><strong>Android Native SDK</strong> — <code>PdfRenderer</code>, penulis PDF halaman-JPEG kecil, &amp; <code>java.util.zip</code> untuk rendering dan penulisan PDF, penguraian EPUB, dan pembuatan EPUB.</li>
    </ul>
  </li>
  <li><strong>Lisensi Proyek Morph:</strong> Didistribusikan di bawah <a href="../LICENSE"><strong>GNU General Public License v3.0 (GPL-3.0)</strong></a>.</li>
</ul>

<hr />

<div align="center">
  <p>Dirancang dan dikembangkan oleh <strong>Rizki Fauzi</strong>.</p>
</div>
