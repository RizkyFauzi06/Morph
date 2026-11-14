<div align="center">

  <img src="assets/Code_Generated_Image.png" alt="Morph Logo" width="112" />

  <h1>Morph</h1>
  <p><strong>Offline File Converter for Android</strong></p>

  <p>
    <strong>English</strong> &nbsp;|&nbsp; <a href="README/README.id.md">Bahasa Indonesia</a>
  </p>

  <p>
    <a href="https://github.com/RizkyFauzi06/Morph/releases"><img src="https://img.shields.io/badge/Download-APK-7952E8?style=flat-square&logo=android&logoColor=white" alt="Download APK" /></a>
    <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-249B69?style=flat-square" alt="Android 8.0+" />
    <img src="https://img.shields.io/badge/Kotlin-100%25-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-555555?style=flat-square" alt="License GPL-3.0" /></a>
  </p>

  <p>
    <a href="#features">Features</a> &nbsp;&bull;&nbsp;
    <a href="#supported-formats">Supported Formats</a> &nbsp;&bull;&nbsp;
    <a href="#installation">Installation</a> &nbsp;&bull;&nbsp;
    <a href="#privacy--permissions">Privacy &amp; Permissions</a> &nbsp;&bull;&nbsp;
    <a href="#tech-stack--license">Tech Stack &amp; License</a>
  </p>

</div>

<hr />

<p>
  <strong>Morph</strong> is a local, offline-first file converter for Android built with Kotlin and Jetpack Compose. It allows you to convert videos, audio tracks, raster images, vector graphics, PDF documents, and e-books directly on your device—with no internet connection, no accounts, and no cloud uploads.
</p>

<h2 id="features">Features</h2>

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>100% Offline &amp; Private</h3>
      <p>Morph does not request internet access. Every conversion, audio extraction, and document operation is processed locally on your device, ensuring your files never leave your phone.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Smart Batch Conversion</h3>
      <p>Select multiple files of different types in one session. Morph automatically groups files by source format so you can assign independent target formats and quality presets per group.</p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>System-Wide Share Integration</h3>
      <p>Convert files directly from your Gallery, File Manager, or messaging apps using Android's native <strong>Share</strong> menu. Supporting both single and multiple file sharing.</p>
    </td>
    <td width="50%" valign="top">
      <h3>Background Queue &amp; Notifications</h3>
      <p>Powered by a sequential conversion queue and an Android Foreground Service. Conversions continue reliably when minimized or with the screen off, complete with real-time progress notifications.</p>
    </td>
  </tr>
</table>

<h2>Interface & Workflow Preview</h2>

<div align="center">
  <table>
    <tr>
      <td width="50%" align="center" valign="top">
        <img src="assets/Homescreen.jpg" alt="Morph Homescreen" width="260" />
        <br /><br />
        <strong>Homescreen & File History</strong>
        <br />
        <sub>Quick file picker, category filters (Documents, Images, Video, Audio), and local conversion history management.</sub>
      </td>
      <td width="50%" align="center" valign="top">
        <img src="assets/Share%20To%20Convert.jpg" alt="Share To Convert" width="260" />
        <br /><br />
        <strong>Share to Convert</strong>
        <br />
        <sub>Trigger conversions directly from Gallery, File Manager, or other apps via the native Android Share sheet.</sub>
      </td>
    </tr>
    <tr>
      <td width="50%" align="center" valign="top">
        <img src="assets/Direct%20Web%20Convertion.jpg" alt="Direct Web Conversion" width="260" />
        <br /><br />
        <strong>Direct Web Format Conversion</strong>
        <br />
        <sub>Convert web-centric media and vector assets (WebP, WebM, SVG) into standard offline-ready formats on-device.</sub>
      </td>
      <td width="50%" align="center" valign="top">
        <img src="assets/Batch%20Processing.jpg" alt="Batch Processing" width="260" />
        <br /><br />
        <strong>Grouped Batch Processing</strong>
        <br />
        <sub>Automatically groups multiple selected files by source extension with independent target formats and quality presets.</sub>
      </td>
    </tr>
  </table>
</div>

<h2 id="supported-formats">Supported Formats</h2>

<p>
  All converted files are saved directly to your device's <code>Downloads/Morph</code> directory, with an option to preserve original filenames.
</p>

<table>
  <thead>
    <tr>
      <th align="left">Category</th>
      <th align="left">Input Formats</th>
      <th align="left">Output Formats</th>
      <th align="left">Capabilities &amp; Notes</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Video</strong></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code>, <code>AVI</code></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code>, <code>GIF</code></td>
      <td>Supports <strong>Instant</strong> lossless stream copy for compatible containers, VP9/Opus encoding for WebM, and aspect-ratio preserved animated GIF export.</td>
    </tr>
    <tr>
      <td><strong>Audio Extraction</strong></td>
      <td><code>MP4</code>, <code>MKV</code>, <code>MOV</code>, <code>WEBM</code></td>
      <td><code>MP3</code>, <code>M4A</code>, <code>WAV</code>, <code>FLAC</code>, <code>OGG</code>, <code>OPUS</code></td>
      <td>Extracts audio tracks directly from video files with configurable quality presets.</td>
    </tr>
    <tr>
      <td><strong>Audio Conversion</strong></td>
      <td><code>MP3</code>, <code>WAV</code>, <code>FLAC</code>, <code>AAC</code>, <code>M4A</code>, <code>OGG</code>, <code>OPUS</code></td>
      <td><code>MP3</code>, <code>WAV</code>, <code>AAC</code>, <code>FLAC</code>, <code>M4A</code>, <code>OGG</code>, <code>OPUS</code></td>
      <td>Uncompressed PCM WAV, lossless FLAC, high-bitrate MP3/AAC, and <strong>Instant</strong> stream copy for compatible M4A/AAC streams.</td>
    </tr>
    <tr>
      <td><strong>Raster Images</strong></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>JPEG</code>, <code>WEBP</code>, <code>BMP</code></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>WEBP</code>, <code>BMP</code>, <code>PDF</code></td>
      <td>Automatic white canvas compositing when converting transparent images to JPG. Multiple images can be merged into a single PDF or exported separately.</td>
    </tr>
    <tr>
      <td><strong>Vector Graphics</strong></td>
      <td><code>SVG</code></td>
      <td><code>PNG</code>, <code>JPG</code>, <code>WEBP</code></td>
      <td>High-resolution vector rasterization powered by AndroidSVG with full alpha transparency retention on PNG.</td>
    </tr>
    <tr>
      <td><strong>PDF Documents</strong></td>
      <td><code>PDF</code></td>
      <td><code>JPG</code>, <code>PNG</code>, <code>WEBP</code>, <code>PDF</code></td>
      <td>Render multi-page PDFs into individual high-resolution images or compress existing PDF file sizes.</td>
    </tr>
    <tr>
      <td><strong>E-Books</strong></td>
      <td><code>EPUB</code></td>
      <td><code>TXT</code></td>
      <td>Parses OPF spine structure to extract clean, readable plain text from digital books.</td>
    </tr>
  </tbody>
</table>

<h3>Quality Presets</h3>
<ul>
  <li><strong>High:</strong> Retains maximum resolution and original bitrate with minimal compression.</li>
  <li><strong>Medium:</strong> Balanced output quality and file size reduction (~50%).</li>
  <li><strong>Low:</strong> Aggressive compression and downscaling to save storage space.</li>
  <li><strong>Instant:</strong> Remuxes compatible media containers without re-encoding for near-instant processing and zero quality loss.</li>
</ul>

<h2 id="installation">Installation</h2>

<h3>Option 1: Install APK (Recommended)</h3>
<p>
  You do not need to clone or build the source code to use Morph:
</p>
<ol>
  <li>Go to the <a href="https://github.com/RizkyFauzi06/Morph/releases"><strong>Releases</strong></a> section of this repository.</li>
  <li>Download the latest <code>Morph-v1.0.0.apk</code> file.</li>
  <li>Open the downloaded APK on your Android device (Android 8.0 or newer) and allow installation from your browser or file manager if prompted.</li>
</ol>

<h3>Option 2: Build from Source</h3>
<p>
  If you want to inspect or compile the project yourself, ensure you have <strong>Android Studio</strong> and <strong>JDK 21</strong> installed:
</p>

```bash
git clone https://github.com/RizkyFauzi06/Morph.git
cd Morph

# Compile Debug APK
./gradlew assembleDebug
```

<p>
  The compiled APK will be generated at <code>app/build/outputs/apk/debug/app-debug.apk</code>.
</p>

<h2 id="privacy--permissions">Privacy &amp; Permissions</h2>

<table>
  <thead>
    <tr>
      <th align="left">Android Permission / Setting</th>
      <th align="left">Purpose</th>
      <th align="center">Status</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>POST_NOTIFICATIONS</code></td>
      <td>Displays real-time conversion queue progress and completion alerts on Android 13+.</td>
      <td align="center">Optional</td>
    </tr>
    <tr>
      <td><code>FOREGROUND_SERVICE</code> &amp; <code>FOREGROUND_SERVICE_DATA_SYNC</code></td>
      <td>Keeps active local conversions running smoothly when you switch apps or lock your screen.</td>
      <td align="center">Required</td>
    </tr>
    <tr>
      <td><code>Unrestricted Battery Usage</code> <em>(System Setting)</em></td>
      <td>Prevents Android's battery saver from terminating long video or audio conversions in the background (configured manually via App Info).</td>
      <td align="center">Recommended</td>
    </tr>
    <tr>
      <td><code>INTERNET</code></td>
      <td><strong>Completely omitted.</strong> Morph cannot connect to the internet or transmit user data.</td>
      <td align="center">Not Used</td>
    </tr>
  </tbody>
</table>

<h2 id="tech-stack--license">Tech Stack &amp; License</h2>

<ul>
  <li><strong>Language &amp; UI:</strong> Kotlin, Jetpack Compose, AndroidX, &amp; Kotlin Coroutines (Apache License 2.0).</li>
  <li><strong>Core Libraries (Third-Party Engines):</strong>
    <ul>
      <li><strong>FFmpegKit Full (8.1.7)</strong> — Audio/video processing (License: <strong>GPL-3.0 / LGPL-3.0</strong>).</li>
      <li><strong>AndroidSVG (1.4)</strong> by Paul LeBeau — SVG vector graphics rendering (License: <strong>Apache License 2.0</strong>).</li>
      <li><strong>Android Native SDK</strong> — <code>PdfRenderer</code>, <code>PdfDocument</code>, &amp; <code>java.util.zip</code> for PDF and EPUB/ZIP archive processing.</li>
    </ul>
  </li>
  <li><strong>Morph Project License:</strong> Distributed under the <a href="LICENSE"><strong>GNU General Public License v3.0 (GPL-3.0)</strong></a>.</li>
</ul>


<hr />

<div align="center">
  <p>Designed and developed by <strong>Rizki Fauzi</strong>.</p>
</div>
