package com.morph.app.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.morph.app.MorphPrefs

/*
 * Centralized Bilingual Localization (Indonesian & English)
 * Fully reactive for Jetpack Compose via CompositionLocalProvider
 * and accessible from non-UI components (Service, Engine, QueueManager).
 */

interface Strings {
    // Navigation & Common
    val tabHome: String
    val tabTools: String
    val tabSettings: String
    val pressBackAgainToExit: String
    val notifPermissionRequiredToast: String
    val webUrlNotSupportedToast: String
    val noFilesFromShareToast: String
    val fileNotFoundToast: String
    fun openWithTitle(ext: String): String
    fun noAppToOpenToast(ext: String): String
    val shareViaTitle: String
    val shareFailedToast: String
    val batterySettingsGuideToast: String
    val conversionStartedBackgroundToast: String
    val languageSetToast: String

    // Welcome Screen
    val welcomeTagline: String
    val welcomeFeatureOfflineTitle: String
    val welcomeFeatureOfflineDesc: String
    val welcomeFeatureFastTitle: String
    val welcomeFeatureFastDesc: String
    val welcomeFeaturePrivacyTitle: String
    val welcomeFeaturePrivacyDesc: String
    val btnGetStarted: String

    // Home Screen
    val notifBannerTitle: String
    val notifBannerDesc: String
    val btnAllow: String
    val batteryBannerTitle: String
    val batteryBannerDesc: String
    val btnLater: String
    val btnLearnHow: String
    val selectNewFile: String
    val selectFile: String
    val selectFileSubtitle: String
    val selectYourFile: String
    val tapToSelectOrShare: String
    fun noFilesInCategory(category: String): String
    val noFilesInCategoryDesc: String
    val btnShowAll: String
    val deleteFileDialogTitle: String
    fun deleteFileDialogMessage(name: String): String
    val btnDelete: String
    val btnCancel: String
    val actionShare: String
    val menuAction: String
    val batteryDialogTitle: String
    val batteryDialogIntro: String
    val batteryStep1: String
    val batteryStep2: String
    val batteryStep3: String
    val batteryNotes: String
    val btnOpenAppInfo: String
    val btnClose: String

    // File Categories
    val categoryAll: String
    val categoryImage: String
    val categoryVideo: String
    val categoryAudio: String
    val categoryPdf: String
    val categoryEbook: String
    val categoryDocument: String
    val categorySpreadsheet: String
    val categoryArchive: String
    val categoryCode: String
    val categoryOther: String

    // Tools Screen
    val toolsTitle: String
    val toolsSubtitle: String
    val toolsConversionTitle: String
    val toolsConversionSubtitle: String
    val toolVideoTitle: String
    val toolVideoDesc: String
    val toolPdfTitle: String
    val toolPdfDesc: String
    val toolAudioTitle: String
    val toolAudioDesc: String
    val toolSvgTitle: String
    val toolSvgDesc: String
    val toolEpubTitle: String
    val toolEpubDesc: String
    val toolImageTitle: String
    val toolImageDesc: String
    val toolsOtherWaysTitle: String
    val toolsOtherWaysSubtitle: String
    val toolShareTitle: String
    val toolShareDesc: String
    val toolShareDetail: String
    val toolBatchTitle: String
    val toolBatchDesc: String
    val toolBatchDetail: String
    val toolOfflineTitle: String
    val toolOfflineDesc: String
    val toolOfflineDetail: String
    val toolFormatTitle: String
    val toolFormatDesc: String
    val toolFormatDetail: String
    val toolExpand: String
    val toolCollapse: String

    // Settings Screen
    val settingsTitle: String
    val settingsSubtitle: String
    val themeSectionTitle: String
    val themeLight: String
    val themeDark: String
    val themeSystem: String
    val languageSectionTitle: String
    val langIndonesian: String
    val langEnglish: String
    val qualitySectionTitle: String
    val qualitySectionDesc: String
    val qualityHigh: String
    val qualityMedium: String
    val qualityLow: String
    val qualityInstant: String
    val qualityDescHigh: String
    val qualityDescMedium: String
    val qualityDescLow: String
    val qualityDescInstant: String
    val behaviorSectionTitle: String
    val keepNameTitle: String
    val keepNameDesc: String
    val closeShareTitle: String
    val closeShareDesc: String
    val batteryOptTitle: String
    val batteryOptStatusOk: String
    val batteryOptStatusRestricted: String
    val aboutSectionTitle: String
    fun aboutVersion(version: String): String
    val aboutAuthor: String
    val licensesSectionTitle: String
    val licensesSectionSubtitle: String
    val licensesDialogIntro: String

    // BottomSheetConverter
    val bottomSheetTitle: String
    fun bottomSheetBatchSubtitle(fileCount: Int, formatCount: Int, sizeStr: String): String
    fun bottomSheetSingleSubtitle(fileName: String, sizeStr: String): String
    val targetFormatHeader: String
    val chooseFormat: String
    val filesCountSuffix: String
    val mergeToPdfTitle: String
    val mergeToPdfDesc: String
    val qualityTitle: String
    fun instantLosslessSupported(src: String, tgt: String): String
    val instantNotAvailable: String
    val btnConvert: String
    fun btnConvertBatch(count: Int): String
    fun btnMergeAndConvert(count: Int): String

    // Conversion Banner & Progress Screen
    fun convertingTo(format: String): String
    fun convertingBatchInfo(curr: Int, total: Int): String
    val conversionInProgress: String
    val cancelConversion: String
    val runningInBackground: String
    val conversionFailed: String
    val failedToConvertDefault: String
    val btnRetry: String
    val cancelDialogTitle: String
    val cancelDialogDesc: String
    val btnConfirmCancel: String
    val btnContinue: String
    val titleFinished: String
    val titleConversionProgress: String
    val btnMinimize: String
    val convertingFileProgress: String
    fun progressBatchFilesCompleted(done: Int, total: Int): String
    val conversionSuccess: String
    val singleFileSuccessDesc: String
    fun batchFilesSuccessDesc(count: Int): String
    val btnViewResult: String
    val btnBackToHome: String
    val conversionPartialSuccess: String
    fun summaryPartial(completed: Int, failed: Int): String
    val defaultErrorMsg: String

    // Queue & Service & Engine (Non-UI)
    val queueWaiting: String
    val queueCompleted: String
    val queueCancelled: String
    val queueFailed: String
    val serviceRunningTitle: String
    val servicePreparingQueue: String
    val serviceStartingConversion: String
    fun notifConvertingBatchTitle(curr: Int, total: Int, name: String): String
    fun notifConvertingSingleTitle(name: String, format: String): String
    val notifActionCancelAll: String
    val notifSuccessTitle: String
    val notifPartialTitle: String
    val notifSuccessBodySingle: String
    fun notifSuccessBodyBatch(count: Int): String
    fun notifSummaryBody(completed: Int, failed: Int): String

    // Engine Step Messages & Errors
    val enginePreparingMedia: String
    fun engineProcessedPercent(percent: Int): String
    fun engineProcessedSeconds(seconds: Long): String
    val engineFinalizingValidating: String
    val engineFinished100: String
    val engineCancelled: String
    fun engineErrSetup(msg: String): String
    val engineErrReadInput: String
    val engineErrInputEmpty: String
    fun engineErrInvalidOutput(reason: String): String
    val engineErrSaveOutput: String
    val engineErrStreamCopyIncompatible: String
    val engineErrFormatNotSupported: String
    fun engineErrFFmpeg(code: Int): String
    val engineReadingPdf: String
    val engineErrPdfRead: String
    val engineErrPdfNoPages: String
    fun engineRenderingPage(page: Int, total: Int): String
    fun enginePagesExtractedSuccess(count: Int): String
    val engineErrSavePdfImage: String
    val engineAnalyzingPdf: String
    val engineErrPdfEmpty: String
    fun engineCompressingPage(page: Int, total: Int): String
    val engineErrSavePdfCompress: String
    val engineLoadingImage: String
    val engineBuildingPdfPages: String
    val engineErrImageToPdf: String
    fun engineBuildingPdfPage(curr: Int, total: Int): String
    val engineSavingPdfDoc: String
    val engineErrSaveMergedPdf: String
    fun engineErrMergePdf(msg: String): String
    val engineProcessingImage: String
    val engineCompressingImage: String
    val engineErrSaveImage: String
    val engineLoadingSvg: String
    val engineErrReadSvg: String
    val engineCalculatingSvgDim: String
    val engineRenderingSvg: String
    val engineCompressingSvgOut: String
    val engineErrSaveSvg: String
    val engineReadingEpub: String
    val engineErrReadEpub: String
    val engineSavingEpubTxt: String
    val engineErrSaveTxt: String

    // Core Validators & Parsers
    val validatorFileNotFound: String
    val validatorNotRegularFile: String
    val validatorOutputEmpty: String
    val validatorCorruptImage: String
    val validatorEmptyTxt: String
    fun validatorUnreadableTxt(msg: String): String
    fun epubErrNotZip(msg: String): String
    val epubStepMetadata: String
    val epubErrNoContainer: String
    fun epubErrNoOpf(path: String): String
    val epubStepSpine: String
    val epubErrEmptySpine: String
    fun epubStepExtractingChapter(curr: Int, total: Int): String
    val epubErrNoChapterText: String
    val epubBookTitlePrefix: String
    val epubBookAuthorPrefix: String
}

object IndonesianStrings : Strings {
    override val tabHome = "Beranda"
    override val tabTools = "Tools"
    override val tabSettings = "Pengaturan"
    override val pressBackAgainToExit = "Tekan sekali lagi untuk keluar"
    override val notifPermissionRequiredToast = "Izin notifikasi diperlukan agar konversi latar belakang berjalan optimal."
    override val webUrlNotSupportedToast = "Morph adalah converter file offline. Format URL atau teks web tidak didukung."
    override val noFilesFromShareToast = "Tidak ada file yang dapat diproses dari Share."
    override val fileNotFoundToast = "File tidak ditemukan"
    override fun openWithTitle(ext: String) = "Buka $ext dengan..."
    override fun noAppToOpenToast(ext: String) = "Tidak ada aplikasi untuk membuka $ext"
    override val shareViaTitle = "Bagikan via..."
    override val shareFailedToast = "Gagal membagikan file"
    override val batterySettingsGuideToast = "Buka Setelan HP > Aplikasi > Morph > Baterai"
    override val conversionStartedBackgroundToast = "Konversi dimulai di latar belakang..."
    override val languageSetToast = "Bahasa diatur ke Bahasa Indonesia"

    override val welcomeTagline = "Cepat · Mudah · Fleksibel"
    override val welcomeFeatureOfflineTitle = "100% Offline"
    override val welcomeFeatureOfflineDesc = "Semua proses berlangsung di perangkat tanpa internet atau cloud."
    override val welcomeFeatureFastTitle = "Konversi Cepat"
    override val welcomeFeatureFastDesc = "Mendukung stream copy instan untuk format yang kompatibel."
    override val welcomeFeaturePrivacyTitle = "Privasi Terjaga"
    override val welcomeFeaturePrivacyDesc = "File Anda tidak pernah meninggalkan ponsel Anda."
    override val btnGetStarted = "Mulai Mengonversi"

    override val notifBannerTitle = "Aktifkan Notifikasi"
    override val notifBannerDesc = "Agar konversi latar belakang tidak dihentikan sistem."
    override val btnAllow = "Izinkan"
    override val batteryBannerTitle = "Lepas Pembatasan Baterai"
    override val batteryBannerDesc = "Agar konversi video/audio panjang tidak dihentikan paksa saat layar mati atau pindah aplikasi."
    override val btnLater = "Nanti"
    override val btnLearnHow = "Lihat Cara"
    override val selectNewFile = "Pilih File Baru"
    override val selectFile = "Pilih File"
    override val selectFileSubtitle = "Pilih file dari perangkat untuk mulai mengonversi."
    override val selectYourFile = "Pilih File Anda"
    override val tapToSelectOrShare = "Ketuk untuk memilih file\natau gunakan menu Share"
    override fun noFilesInCategory(category: String) = "Tidak ada file $category"
    override val noFilesInCategoryDesc = "Belum ada file dalam kategori ini. Coba kategori lain atau konversi file baru."
    override val btnShowAll = "Tampilkan Semua"
    override val deleteFileDialogTitle = "Hapus File"
    override fun deleteFileDialogMessage(name: String) = "Apakah Anda yakin ingin menghapus $name secara permanen?"
    override val btnDelete = "Hapus"
    override val btnCancel = "Batal"
    override val actionShare = "Bagikan"
    override val menuAction = "Menu Aksi"
    override val batteryDialogTitle = "Cara Menonaktifkan Pembatasan Baterai"
    override val batteryDialogIntro = "Sistem Android sering menghentikan proses latar belakang untuk menghemat daya. Ikuti langkah berikut agar konversi tetap berjalan lancar:"
    override val batteryStep1 = "1. Ketuk tombol \"Buka Info Aplikasi\" di bawah."
    override val batteryStep2 = "2. Pilih menu \"Baterai\" atau \"Penggunaan baterai aplikasi\"."
    override val batteryStep3 = "3. Ubah pengaturan menjadi \"Tidak dibatasi\" (Unrestricted)."
    override val batteryNotes = "Catatan per merek HP:\n• Xiaomi / Redmi / Poco: Pilih \"Penghemat baterai\" → \"Tidak ada pembatasan\".\n• Samsung: Pilih \"Baterai\" → \"Tidak dibatasi\".\n• Oppo / Realme / Vivo: Aktifkan \"Izinkan aktivitas latar belakang\"."
    override val btnOpenAppInfo = "Buka Info Aplikasi"
    override val btnClose = "Tutup"

    override val categoryAll = "Semua"
    override val categoryImage = "Gambar"
    override val categoryVideo = "Video"
    override val categoryAudio = "Audio"
    override val categoryPdf = "PDF"
    override val categoryEbook = "E-book"
    override val categoryDocument = "Dokumen"
    override val categorySpreadsheet = "Spreadsheet"
    override val categoryArchive = "Arsip"
    override val categoryCode = "Kode"
    override val categoryOther = "Lainnya"

    override val toolsTitle = "Tools"
    override val toolsSubtitle = "Kenali fitur dan kemampuan Morph."
    override val toolsConversionTitle = "Alat Konversi"
    override val toolsConversionSubtitle = "Konversi file bawaan Morph."
    override val toolVideoTitle = "Video Conversion"
    override val toolVideoDesc = "Mengubah container video dengan proses cepat tanpa encoding ulang. Ukuran hasil dapat sedikit berbeda."
    override val toolPdfTitle = "PDF Toolkit"
    override val toolPdfDesc = "Kompres dokumen PDF atau ubah setiap halaman menjadi gambar yang dapat dibuka dan dibagikan."
    override val toolAudioTitle = "Audio Extraction"
    override val toolAudioDesc = "Ekstrak jalur audio dari video dan simpan dalam berbagai format audio."
    override val toolSvgTitle = "SVG Conversion"
    override val toolSvgDesc = "Ubah grafik vektor SVG menjadi gambar raster yang dapat dibuka di galeri atau dibagikan."
    override val toolEpubTitle = "EPUB Text Extraction"
    override val toolEpubDesc = "Ekstrak isi dan bab buku digital menjadi satu file teks yang lebih ringan dan mudah dibaca."
    override val toolImageTitle = "Image Conversion"
    override val toolImageDesc = "Ubah format gambar dengan penanganan transparansi dan pilihan output yang beragam."
    override val toolsOtherWaysTitle = "Cara Lain Menggunakan Morph"
    override val toolsOtherWaysSubtitle = "Gunakan Morph langsung dari aplikasi lain."
    override val toolShareTitle = "Share to Morph"
    override val toolShareDesc = "Kirim file langsung dari aplikasi lain ke Morph melalui menu Share Android."
    override val toolShareDetail = "Pilih satu atau beberapa file dari Gallery, File Manager, atau aplikasi lain yang mendukung berbagi file. Morph menerima file tersebut dan menyiapkan pilihan konversi tanpa perlu mencarinya kembali secara manual."
    override val toolBatchTitle = "Batch Conversion"
    override val toolBatchDesc = "Pilih beberapa file sekaligus dan proses semuanya dalam satu sesi konversi."
    override val toolBatchDetail = "Morph dapat mengelompokkan file berdasarkan format sumber sehingga setiap kelompok dapat memiliki format tujuan dan pengaturan yang sesuai."
    override val toolOfflineTitle = "Offline Conversion"
    override val toolOfflineDesc = "Konversi file secara lokal di perangkat."
    override val toolOfflineDetail = "Untuk format yang didukung, proses konversi dilakukan tanpa perlu mengunggah file ke layanan cloud."
    override val toolFormatTitle = "Format Support"
    override val toolFormatDesc = "Lihat format sumber dan tujuan yang tersedia."
    override val toolFormatDetail = "Video: MP4, MKV, MOV, WEBM, AVI\nAudio: MP3, WAV, FLAC, AAC, M4A, OGG, OPUS\nGambar: PNG, JPG, WEBP, BMP, SVG\nDokumen: PDF, TXT, EPUB"
    override val toolExpand = "Buka"
    override val toolCollapse = "Tutup"

    override val settingsTitle = "Pengaturan"
    override val settingsSubtitle = "Konfigurasi tampilan dan perilaku Morph."
    override val themeSectionTitle = "Tema Aplikasi"
    override val themeLight = "Terang"
    override val themeDark = "Gelap"
    override val themeSystem = "Sistem"
    override val languageSectionTitle = "Bahasa / Language"
    override val langIndonesian = "Bahasa Indonesia"
    override val langEnglish = "English"
    override val qualitySectionTitle = "Kualitas Konversi Default"
    override val qualitySectionDesc = "Preset bawaan yang digunakan saat membuka panel konversi. Dapat diubah per sesi."
    override val qualityHigh = "Tinggi"
    override val qualityMedium = "Sedang"
    override val qualityLow = "Rendah"
    override val qualityInstant = "Instan"
    override val qualityDescHigh = "Tinggi: bitrate 100% dipertahankan tanpa kompresi tambahan."
    override val qualityDescMedium = "Sedang: kompresi seimbang, ukuran berkurang ~50%."
    override val qualityDescLow = "Rendah: kompresi agresif untuk hemat ruang."
    override val qualityDescInstant = "Instan: stream copy cepat tanpa re-encode (kualitas asli, hanya untuk format yang kompatibel)."
    override val behaviorSectionTitle = "Perilaku Aplikasi"
    override val keepNameTitle = "Pertahankan Nama Asli"
    override val keepNameDesc = "Pertahankan nama dasar file asli. Morph tidak menambahkan imbuhan nama baru, kecuali diperlukan untuk menghindari nama yang bertabrakan."
    override val closeShareTitle = "Tutup Otomatis (Share)"
    override val closeShareDesc = "Tutup bottom sheet setelah format konversi dipilih tanpa mengarahkan pengguna ke aplikasi Morph."
    override val batteryOptTitle = "Optimasi Baterai Latar Belakang"
    override val batteryOptStatusOk = "Status: Tidak dibatasi (Optimal)"
    override val batteryOptStatusRestricted = "Status: Dibatasi · Ketuk untuk melihat cara mengatur"
    override val aboutSectionTitle = "Tentang Morph"
    override fun aboutVersion(version: String) = "Versi $version · Offline File Converter"
    override val aboutAuthor = "Dirancang oleh Rizki Fauzi"
    override val licensesSectionTitle = "Lisensi Open Source"
    override val licensesSectionSubtitle = "GPL-3.0, Apache 2.0 & pustaka pihak ketiga"
    override val licensesDialogIntro = "Morph didistribusikan di bawah GNU General Public License v3.0 (GPL-3.0) dan menggunakan pustaka open-source berikut:"

    override val bottomSheetTitle = "Konversi File"
    override fun bottomSheetBatchSubtitle(fileCount: Int, formatCount: Int, sizeStr: String) =
        "$fileCount file terpilih • $formatCount format$sizeStr"
    override fun bottomSheetSingleSubtitle(fileName: String, sizeStr: String) = "$fileName$sizeStr"
    override val targetFormatHeader = "Format Tujuan"
    override val chooseFormat = "PILIH"
    override val filesCountSuffix = " terpilih"
    override val mergeToPdfTitle = "Gabung menjadi satu PDF"
    override val mergeToPdfDesc = "Jika ada banyak file gambar."
    override val qualityTitle = "Kualitas"
    override fun instantLosslessSupported(src: String, tgt: String) =
        "Lossless (Instan) didukung untuk ${src.uppercase()} → ${tgt.uppercase()}"
    override val instantNotAvailable = "Instant tidak tersedia untuk format ini. Morph menggunakan kualitas tertinggi."
    override val btnConvert = "Konversi"
    override fun btnConvertBatch(count: Int) = "Konversi ($count File)"
    override fun btnMergeAndConvert(count: Int) = "Gabung & Konversi ($count File)"

    override fun convertingTo(format: String) = "Mengonversi ke ${format.uppercase()}"
    override fun convertingBatchInfo(curr: Int, total: Int) = "Mengonversi ($curr/$total)"
    override val conversionInProgress = "Proses konversi sedang berjalan"
    override val cancelConversion = "Batalkan Konversi"
    override val runningInBackground = "Berjalan di latar belakang"
    override val conversionFailed = "Konversi Gagal"
    override val failedToConvertDefault = "Gagal dikonversi"
    override val btnRetry = "Coba Lagi"
    override val cancelDialogTitle = "Batalkan Konversi?"
    override val cancelDialogDesc = "Proses konversi yang sedang berjalan akan dihentikan."
    override val btnConfirmCancel = "Batalkan"
    override val btnContinue = "Lanjutkan"
    override val titleFinished = "Selesai"
    override val titleConversionProgress = "Progres Konversi"
    override val btnMinimize = "Minimalkan"
    override val convertingFileProgress = "Mengonversi File..."
    override fun progressBatchFilesCompleted(done: Int, total: Int) = "$done dari $total file selesai"
    override val conversionSuccess = "Konversi Berhasil!"
    override val singleFileSuccessDesc = "File Anda telah berhasil dikonversi."
    override fun batchFilesSuccessDesc(count: Int) = "$count file berhasil dikonversi."
    override val btnViewResult = "Lihat Hasil"
    override val btnBackToHome = "Kembali ke Beranda"
    override val conversionPartialSuccess = "Konversi Selesai Sebagian"
    override fun summaryPartial(completed: Int, failed: Int) = "$completed berhasil, $failed gagal diproses."
    override val defaultErrorMsg = "Terjadi kesalahan saat memproses file."

    override val queueWaiting = "Menunggu antrean..."
    override val queueCompleted = "Konversi Selesai"
    override val queueCancelled = "Dibatalkan"
    override val queueFailed = "Konversi Gagal"
    override val serviceRunningTitle = "Morph Berjalan"
    override val servicePreparingQueue = "Menyiapkan antrean konversi..."
    override val serviceStartingConversion = "Memulai konversi..."
    override fun notifConvertingBatchTitle(curr: Int, total: Int, name: String) =
        "Mengonversi ($curr/$total): $name"
    override fun notifConvertingSingleTitle(name: String, format: String) =
        "Mengonversi $name → ${format.uppercase()}"
    override val notifActionCancelAll = "Batal Semua"
    override val notifSuccessTitle = "✅ Konversi Selesai"
    override val notifPartialTitle = "⚠️ Konversi Selesai Sebagian"
    override val notifSuccessBodySingle = "1 berkas berhasil dikonversi ke Downloads/Morph"
    override fun notifSuccessBodyBatch(count: Int) = "$count berkas berhasil dikonversi ke Downloads/Morph"
    override fun notifSummaryBody(completed: Int, failed: Int) = "$completed berhasil, $failed gagal diproses."

    override val enginePreparingMedia = "Menyiapkan file multimedia..."
    override fun engineProcessedPercent(percent: Int) = "Telah diproses: $percent%"
    override fun engineProcessedSeconds(seconds: Long) = "Telah diproses: ${seconds}s"
    override val engineFinalizingValidating = "Menyelesaikan dan memvalidasi berkas..."
    override val engineFinished100 = "Selesai 100%"
    override val engineCancelled = "Konversi dibatalkan."
    override fun engineErrSetup(msg: String) = "Gagal setup konversi: $msg"
    override val engineErrReadInput = "Tidak dapat membaca berkas input."
    override val engineErrInputEmpty = "Berkas input kosong atau tidak valid."
    override fun engineErrInvalidOutput(reason: String) = "Hasil tidak valid: $reason"
    override val engineErrSaveOutput = "Gagal menyimpan hasil ke direktori Downloads/Morph"
    override val engineErrStreamCopyIncompatible =
        "Stream Copy tidak kompatibel untuk codec stream berkas ini. Gunakan kualitas Tinggi atau Sedang."
    override val engineErrFormatNotSupported =
        "Format tujuan tidak mendukung salah satu stream media dari berkas sumber."
    override fun engineErrFFmpeg(code: Int) = "Gagal Konversi FFmpeg (Kode: $code)"
    override val engineReadingPdf = "Membaca dokumen PDF..."
    override val engineErrPdfRead = "Tidak dapat membaca dokumen PDF."
    override val engineErrPdfNoPages = "Dokumen PDF tidak memiliki halaman."
    override fun engineRenderingPage(page: Int, total: Int) = "Merender halaman $page dari $total..."
    override fun enginePagesExtractedSuccess(count: Int) = "$count halaman berhasil diekstrak ke Downloads/Morph"
    override val engineErrSavePdfImage = "Gagal menyimpan hasil gambar PDF"
    override val engineAnalyzingPdf = "Menganalisis ukuran PDF..."
    override val engineErrPdfEmpty = "Dokumen PDF kosong."
    override fun engineCompressingPage(page: Int, total: Int) = "Mengompres halaman $page dari $total..."
    override val engineErrSavePdfCompress = "Gagal menyimpan hasil kompresi PDF"
    override val engineLoadingImage = "Memuat gambar..."
    override val engineBuildingPdfPages = "Menyusun lembar PDF..."
    override val engineErrImageToPdf = "Gagal membuat PDF dari gambar"
    override fun engineBuildingPdfPage(curr: Int, total: Int) = "Menyusun lembar $curr dari $total..."
    override val engineSavingPdfDoc = "Menyimpan dokumen PDF..."
    override val engineErrSaveMergedPdf = "Gagal menyimpan dokumen PDF gabungan"
    override fun engineErrMergePdf(msg: String) = "Gagal menggabungkan gambar ke PDF: $msg"
    override val engineProcessingImage = "Memproses gambar..."
    override val engineCompressingImage = "Mengompresi gambar..."
    override val engineErrSaveImage = "Gagal menyimpan gambar hasil"
    override val engineLoadingSvg = "Memuat file SVG..."
    override val engineErrReadSvg = "Tidak dapat membaca data dari berkas SVG."
    override val engineCalculatingSvgDim = "Menghitung dimensi vektor SVG..."
    override val engineRenderingSvg = "Merender vektor SVG ke kanvas..."
    override val engineCompressingSvgOut = "Mengompresi gambar output..."
    override val engineErrSaveSvg = "Gagal menyimpan gambar SVG"
    override val engineReadingEpub = "Membaca berkas EPUB..."
    override val engineErrReadEpub = "Tidak dapat membaca berkas EPUB."
    override val engineSavingEpubTxt = "Menyimpan teks hasil ekstraksi..."
    override val engineErrSaveTxt = "Gagal menyimpan file TXT"

    override val validatorFileNotFound = "Berkas output tidak ditemukan di sistem penyimpanan."
    override val validatorNotRegularFile = "Path output bukan merupakan berkas reguler."
    override val validatorOutputEmpty = "Berkas output kosong (ukuran 0 byte)."
    override val validatorCorruptImage = "Format gambar output rusak atau tidak dapat diuraikan."
    override val validatorEmptyTxt = "Berkas teks hasil konversi kosong."
    override fun validatorUnreadableTxt(msg: String) = "Berkas teks tidak dapat dibaca: $msg"
    override fun epubErrNotZip(msg: String) = "Berkas bukan kontainer ZIP/EPUB yang valid: $msg"
    override val epubStepMetadata = "Mencari berkas metadata EPUB..."
    override val epubErrNoContainer = "META-INF/container.xml atau rootfile OPF tidak ditemukan di dalam berkas EPUB."
    override fun epubErrNoOpf(path: String) = "Berkas paket OPF ($path) tidak ditemukan di dalam kontainer EPUB."
    override val epubStepSpine = "Membaca struktur dan urutan membaca (Spine)..."
    override val epubErrEmptySpine = "OPF Spine tidak memiliki itemref urutan membaca yang valid."
    override fun epubStepExtractingChapter(curr: Int, total: Int) = "Mengekstrak bab $curr dari $total..."
    override val epubErrNoChapterText = "Tidak ditemukan teks bab yang dapat dibaca dalam urutan Spine EPUB."
    override val epubBookTitlePrefix = "JUDUL: "
    override val epubBookAuthorPrefix = "PENULIS: "
}

object EnglishStrings : Strings {
    override val tabHome = "Home"
    override val tabTools = "Tools"
    override val tabSettings = "Settings"
    override val pressBackAgainToExit = "Press back again to exit"
    override val notifPermissionRequiredToast = "Notification permission is required for optimal background conversions."
    override val webUrlNotSupportedToast = "Morph is an offline file converter. Web URLs or plain text are not supported."
    override val noFilesFromShareToast = "No files found to process from Share."
    override val fileNotFoundToast = "File not found"
    override fun openWithTitle(ext: String) = "Open $ext with..."
    override fun noAppToOpenToast(ext: String) = "No app found to open $ext"
    override val shareViaTitle = "Share via..."
    override val shareFailedToast = "Failed to share file"
    override val batterySettingsGuideToast = "Open Settings > Apps > Morph > Battery"
    override val conversionStartedBackgroundToast = "Conversion started in background..."
    override val languageSetToast = "Language set to English"

    override val welcomeTagline = "Fast · Simple · Flexible"
    override val welcomeFeatureOfflineTitle = "100% Offline"
    override val welcomeFeatureOfflineDesc = "All processing happens on device without internet or cloud."
    override val welcomeFeatureFastTitle = "Fast Conversion"
    override val welcomeFeatureFastDesc = "Supports instant stream copy for compatible formats."
    override val welcomeFeaturePrivacyTitle = "Privacy Protected"
    override val welcomeFeaturePrivacyDesc = "Your files never leave your device."
    override val btnGetStarted = "Start Converting"

    override val notifBannerTitle = "Enable Notifications"
    override val notifBannerDesc = "Keeps background conversions running reliably."
    override val btnAllow = "Allow"
    override val batteryBannerTitle = "Disable Battery Restrictions"
    override val batteryBannerDesc = "Prevents long video/audio conversions from stopping when the screen turns off."
    override val btnLater = "Later"
    override val btnLearnHow = "Learn How"
    override val selectNewFile = "Select New File"
    override val selectFile = "Select File"
    override val selectFileSubtitle = "Choose a file from your device to start converting."
    override val selectYourFile = "Select Your File"
    override val tapToSelectOrShare = "Tap to select a file\nor use the Share menu"
    override fun noFilesInCategory(category: String) = "No $category files"
    override val noFilesInCategoryDesc = "No files found in this category. Try another category or convert a new file."
    override val btnShowAll = "Show All"
    override val deleteFileDialogTitle = "Delete File"
    override fun deleteFileDialogMessage(name: String) = "Are you sure you want to permanently delete $name?"
    override val btnDelete = "Delete"
    override val btnCancel = "Cancel"
    override val actionShare = "Share"
    override val menuAction = "Action Menu"
    override val batteryDialogTitle = "How to Disable Battery Restrictions"
    override val batteryDialogIntro = "Android often terminates background tasks to save power. Follow these steps so conversions run uninterrupted:"
    override val batteryStep1 = "1. Tap the \"Open App Info\" button below."
    override val batteryStep2 = "2. Select \"Battery\" or \"App battery usage\"."
    override val batteryStep3 = "3. Set the option to \"Unrestricted\"."
    override val batteryNotes = "Device-specific notes:\n• Xiaomi / Redmi / Poco: Select \"Battery saver\" → \"No restrictions\".\n• Samsung: Select \"Battery\" → \"Unrestricted\".\n• Oppo / Realme / Vivo: Enable \"Allow background activity\"."
    override val btnOpenAppInfo = "Open App Info"
    override val btnClose = "Close"

    override val categoryAll = "All"
    override val categoryImage = "Images"
    override val categoryVideo = "Video"
    override val categoryAudio = "Audio"
    override val categoryPdf = "PDF"
    override val categoryEbook = "E-book"
    override val categoryDocument = "Documents"
    override val categorySpreadsheet = "Spreadsheets"
    override val categoryArchive = "Archives"
    override val categoryCode = "Code"
    override val categoryOther = "Others"

    override val toolsTitle = "Tools"
    override val toolsSubtitle = "Discover Morph features and capabilities."
    override val toolsConversionTitle = "Conversion Tools"
    override val toolsConversionSubtitle = "Morph built-in conversion tools."
    override val toolVideoTitle = "Video Conversion"
    override val toolVideoDesc = "Quickly re-container videos without re-encoding. Output file size may vary slightly."
    override val toolPdfTitle = "PDF Toolkit"
    override val toolPdfDesc = "Compress PDF documents or extract each page into shareable images."
    override val toolAudioTitle = "Audio Extraction"
    override val toolAudioDesc = "Extract audio tracks from video files and save them in various audio formats."
    override val toolSvgTitle = "SVG Conversion"
    override val toolSvgDesc = "Convert SVG vector graphics into raster images for gallery viewing and sharing."
    override val toolEpubTitle = "EPUB Text Extraction"
    override val toolEpubDesc = "Extract digital book chapters into a clean, lightweight text file."
    override val toolImageTitle = "Image Conversion"
    override val toolImageDesc = "Convert image formats with transparency handling and multiple output options."
    override val toolsOtherWaysTitle = "More Ways to Use Morph"
    override val toolsOtherWaysSubtitle = "Use Morph directly from other apps."
    override val toolShareTitle = "Share to Morph"
    override val toolShareDesc = "Send files directly from other apps to Morph via Android's Share menu."
    override val toolShareDetail = "Select one or multiple files from Gallery, File Manager, or other apps supporting file sharing. Morph receives them and prepares conversion options automatically."
    override val toolBatchTitle = "Batch Conversion"
    override val toolBatchDesc = "Select multiple files at once and convert them all in a single session."
    override val toolBatchDetail = "Morph groups files by source format so each group can have its own target format and quality settings."
    override val toolOfflineTitle = "Offline Conversion"
    override val toolOfflineDesc = "Convert files locally on your device."
    override val toolOfflineDetail = "All supported conversions are processed on-device without uploading files to cloud services."
    override val toolFormatTitle = "Format Support"
    override val toolFormatDesc = "View available source and target formats."
    override val toolFormatDetail = "Video: MP4, MKV, MOV, WEBM, AVI\nAudio: MP3, WAV, FLAC, AAC, M4A, OGG, OPUS\nImages: PNG, JPG, WEBP, BMP, SVG\nDocuments: PDF, TXT, EPUB"
    override val toolExpand = "Expand"
    override val toolCollapse = "Collapse"

    override val settingsTitle = "Settings"
    override val settingsSubtitle = "Configure Morph display and behavior."
    override val themeSectionTitle = "App Theme"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeSystem = "System"
    override val languageSectionTitle = "Language / Bahasa"
    override val langIndonesian = "Bahasa Indonesia"
    override val langEnglish = "English"
    override val qualitySectionTitle = "Default Conversion Quality"
    override val qualitySectionDesc = "Default preset used when opening the conversion sheet. Can be adjusted per session."
    override val qualityHigh = "High"
    override val qualityMedium = "Medium"
    override val qualityLow = "Low"
    override val qualityInstant = "Instant"
    override val qualityDescHigh = "High: 100% bitrate preserved without additional compression."
    override val qualityDescMedium = "Medium: balanced compression, ~50% size reduction."
    override val qualityDescLow = "Low: aggressive compression to save space."
    override val qualityDescInstant = "Instant: fast stream copy without re-encoding (original quality, compatible formats only)."
    override val behaviorSectionTitle = "App Behavior"
    override val keepNameTitle = "Keep Original Filename"
    override val keepNameDesc = "Preserve the original base filename. Morph does not add extra prefixes or suffixes unless needed to prevent collisions."
    override val closeShareTitle = "Auto-Close (Share)"
    override val closeShareDesc = "Close the sheet after choosing the target format without opening Morph's main screen."
    override val batteryOptTitle = "Background Battery Optimization"
    override val batteryOptStatusOk = "Status: Unrestricted (Optimal)"
    override val batteryOptStatusRestricted = "Status: Restricted · Tap to see instructions"
    override val aboutSectionTitle = "About Morph"
    override fun aboutVersion(version: String) = "Version $version · Offline File Converter"
    override val aboutAuthor = "Designed by Rizki Fauzi"
    override val licensesSectionTitle = "Open Source Licenses"
    override val licensesSectionSubtitle = "GPL-3.0, Apache 2.0 & third-party libraries"
    override val licensesDialogIntro = "Morph is distributed under the GNU General Public License v3.0 (GPL-3.0) and uses the following open-source libraries:"

    override val bottomSheetTitle = "Convert Files"
    override fun bottomSheetBatchSubtitle(fileCount: Int, formatCount: Int, sizeStr: String) =
        "$fileCount files selected • $formatCount formats$sizeStr"
    override fun bottomSheetSingleSubtitle(fileName: String, sizeStr: String) = "$fileName$sizeStr"
    override val targetFormatHeader = "Target Format"
    override val chooseFormat = "SELECT"
    override val filesCountSuffix = " selected"
    override val mergeToPdfTitle = "Merge into a single PDF"
    override val mergeToPdfDesc = "When multiple image files are present."
    override val qualityTitle = "Quality"
    override fun instantLosslessSupported(src: String, tgt: String) =
        "Lossless (Instant) supported for ${src.uppercase()} → ${tgt.uppercase()}"
    override val instantNotAvailable = "Instant is not available for this format. Morph will use High quality."
    override val btnConvert = "Convert"
    override fun btnConvertBatch(count: Int) = "Convert ($count Files)"
    override fun btnMergeAndConvert(count: Int) = "Merge & Convert ($count Files)"

    override fun convertingTo(format: String) = "Converting to ${format.uppercase()}"
    override fun convertingBatchInfo(curr: Int, total: Int) = "Converting ($curr/$total)"
    override val conversionInProgress = "Conversion in progress"
    override val cancelConversion = "Cancel Conversion"
    override val runningInBackground = "Running in background"
    override val conversionFailed = "Conversion Failed"
    override val failedToConvertDefault = "Failed to convert"
    override val btnRetry = "Retry"
    override val cancelDialogTitle = "Cancel Conversion?"
    override val cancelDialogDesc = "The current conversion process will be stopped."
    override val btnConfirmCancel = "Cancel"
    override val btnContinue = "Continue"
    override val titleFinished = "Finished"
    override val titleConversionProgress = "Conversion Progress"
    override val btnMinimize = "Minimize"
    override val convertingFileProgress = "Converting Files..."
    override fun progressBatchFilesCompleted(done: Int, total: Int) = "$done of $total files completed"
    override val conversionSuccess = "Conversion Successful!"
    override val singleFileSuccessDesc = "Your file has been converted successfully."
    override fun batchFilesSuccessDesc(count: Int) = "$count files converted successfully."
    override val btnViewResult = "View Result"
    override val btnBackToHome = "Back to Home"
    override val conversionPartialSuccess = "Conversion Partially Completed"
    override fun summaryPartial(completed: Int, failed: Int) = "$completed succeeded, $failed failed."
    override val defaultErrorMsg = "An error occurred while processing the file."

    override val queueWaiting = "Waiting in queue..."
    override val queueCompleted = "Conversion Completed"
    override val queueCancelled = "Cancelled"
    override val queueFailed = "Conversion Failed"
    override val serviceRunningTitle = "Morph Running"
    override val servicePreparingQueue = "Preparing conversion queue..."
    override val serviceStartingConversion = "Starting conversion..."
    override fun notifConvertingBatchTitle(curr: Int, total: Int, name: String) =
        "Converting ($curr/$total): $name"
    override fun notifConvertingSingleTitle(name: String, format: String) =
        "Converting $name → ${format.uppercase()}"
    override val notifActionCancelAll = "Cancel All"
    override val notifSuccessTitle = "✅ Conversion Completed"
    override val notifPartialTitle = "⚠️ Conversion Partially Completed"
    override val notifSuccessBodySingle = "1 file successfully converted to Downloads/Morph"
    override fun notifSuccessBodyBatch(count: Int) = "$count files successfully converted to Downloads/Morph"
    override fun notifSummaryBody(completed: Int, failed: Int) = "$completed succeeded, $failed failed."

    override val enginePreparingMedia = "Preparing multimedia file..."
    override fun engineProcessedPercent(percent: Int) = "Processed: $percent%"
    override fun engineProcessedSeconds(seconds: Long) = "Processed: ${seconds}s"
    override val engineFinalizingValidating = "Finalizing and validating file..."
    override val engineFinished100 = "Completed 100%"
    override val engineCancelled = "Conversion cancelled."
    override fun engineErrSetup(msg: String) = "Failed to setup conversion: $msg"
    override val engineErrReadInput = "Cannot read input file."
    override val engineErrInputEmpty = "Input file is empty or invalid."
    override fun engineErrInvalidOutput(reason: String) = "Invalid output: $reason"
    override val engineErrSaveOutput = "Failed to save output to Downloads/Morph"
    override val engineErrStreamCopyIncompatible =
        "Stream Copy is incompatible with this file's stream codec. Please use High or Medium quality."
    override val engineErrFormatNotSupported =
        "Target format does not support one of the source media streams."
    override fun engineErrFFmpeg(code: Int) = "FFmpeg conversion failed (Code: $code)"
    override val engineReadingPdf = "Reading PDF document..."
    override val engineErrPdfRead = "Cannot read PDF document."
    override val engineErrPdfNoPages = "PDF document has no pages."
    override fun engineRenderingPage(page: Int, total: Int) = "Rendering page $page of $total..."
    override fun enginePagesExtractedSuccess(count: Int) = "$count pages successfully extracted to Downloads/Morph"
    override val engineErrSavePdfImage = "Failed to save PDF images"
    override val engineAnalyzingPdf = "Analyzing PDF size..."
    override val engineErrPdfEmpty = "PDF document is empty."
    override fun engineCompressingPage(page: Int, total: Int) = "Compressing page $page of $total..."
    override val engineErrSavePdfCompress = "Failed to save compressed PDF"
    override val engineLoadingImage = "Loading image..."
    override val engineBuildingPdfPages = "Building PDF pages..."
    override val engineErrImageToPdf = "Failed to create PDF from image"
    override fun engineBuildingPdfPage(curr: Int, total: Int) = "Building page $curr of $total..."
    override val engineSavingPdfDoc = "Saving PDF document..."
    override val engineErrSaveMergedPdf = "Failed to save merged PDF document"
    override fun engineErrMergePdf(msg: String) = "Failed to merge images to PDF: $msg"
    override val engineProcessingImage = "Processing image..."
    override val engineCompressingImage = "Compressing image..."
    override val engineErrSaveImage = "Failed to save output image"
    override val engineLoadingSvg = "Loading SVG file..."
    override val engineErrReadSvg = "Cannot read SVG file data."
    override val engineCalculatingSvgDim = "Calculating SVG vector dimensions..."
    override val engineRenderingSvg = "Rendering SVG vector to canvas..."
    override val engineCompressingSvgOut = "Compressing output image..."
    override val engineErrSaveSvg = "Failed to save SVG image"
    override val engineReadingEpub = "Reading EPUB file..."
    override val engineErrReadEpub = "Cannot read EPUB file."
    override val engineSavingEpubTxt = "Saving extracted text..."
    override val engineErrSaveTxt = "Failed to save TXT file"

    override val validatorFileNotFound = "Output file not found in storage."
    override val validatorNotRegularFile = "Output path is not a regular file."
    override val validatorOutputEmpty = "Output file is empty (0 bytes)."
    override val validatorCorruptImage = "Output image format is corrupted or cannot be parsed."
    override val validatorEmptyTxt = "Converted text file is empty."
    override fun validatorUnreadableTxt(msg: String) = "Text file cannot be read: $msg"
    override fun epubErrNotZip(msg: String) = "File is not a valid ZIP/EPUB container: $msg"
    override val epubStepMetadata = "Finding EPUB metadata file..."
    override val epubErrNoContainer = "META-INF/container.xml or OPF rootfile not found in EPUB file."
    override fun epubErrNoOpf(path: String) = "OPF package file ($path) not found in EPUB container."
    override val epubStepSpine = "Reading structure and reading order (Spine)..."
    override val epubErrEmptySpine = "OPF Spine has no valid reading order itemref."
    override fun epubStepExtractingChapter(curr: Int, total: Int) = "Extracting chapter $curr of $total..."
    override val epubErrNoChapterText = "No readable chapter text found in EPUB Spine."
    override val epubBookTitlePrefix = "TITLE: "
    override val epubBookAuthorPrefix = "AUTHOR: "
}

val LocalAppStrings = staticCompositionLocalOf<Strings> { IndonesianStrings }

object AppStrings {
    private var _currentLanguage: String = "id"
    val currentLanguage: String get() = _currentLanguage

    val current: Strings
        @Composable
        @ReadOnlyComposable
        get() = LocalAppStrings.current

    fun init(context: Context) {
        _currentLanguage = MorphPrefs(context).appLanguage
    }

    fun setLanguage(lang: String) {
        _currentLanguage = if (lang.equals("en", ignoreCase = true)) "en" else "id"
    }

    fun get(lang: String = _currentLanguage): Strings {
        return if (lang.equals("en", ignoreCase = true)) EnglishStrings else IndonesianStrings
    }
}
