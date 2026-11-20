package com.morph.app

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.JobStatus
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.morph.app.core.MorphOutputFile
import com.morph.app.core.OutputManager
import com.morph.app.core.SelectedFileItem
import com.morph.app.core.Strings
import com.morph.app.ui.components.MorphBottomNavigation
import com.morph.app.ui.components.MorphNavItem
import com.morph.app.ui.components.MorphTopBar
import com.morph.app.ui.screens.BottomSheetConverter
import com.morph.app.ui.screens.HomeScreen
import com.morph.app.ui.screens.ProgressScreen
import com.morph.app.ui.screens.SettingsScreen
import com.morph.app.ui.screens.ToolsScreen
import com.morph.app.ui.screens.WelcomeScreen
import com.morph.app.ui.theme.LocalMorphColors
import com.morph.app.ui.theme.MorphMotion
import com.morph.app.ui.theme.MorphTheme
import java.io.File
import android.os.PowerManager
import android.provider.Settings

/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

enum class MorphTab {
    Home, Tools, Settings
}

/** Top-level standalone screens; switching between them is one AnimatedContent. */
private enum class MorphScreen { Onboarding, Progress, Main }

class MainActivity : ComponentActivity() {

    // Notification permission launcher (PBI 7.5)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, AppStrings.get().notifPermissionRequiredToast, Toast.LENGTH_LONG).show()
        }
    }

    // BUG-01: legacy storage permission launcher (API 26–28 write, API 29 read)
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) return@registerForActivityResult
        val permission = legacyStoragePermission() ?: return@registerForActivityResult
        // After "Don't ask again" Android 8-10 never shows the dialog again (rationale stays false).
        // On 8-9 that makes every save fail, unless a custom SAF folder is set (it needs no permission).
        val permanentlyDenied = !shouldShowRequestPermissionRationale(permission)
        if (permanentlyDenied && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            OutputManager.validCustomTreeUri(this) == null
        ) {
            showStorageSettingsDialog()
        } else {
            Toast.makeText(this, AppStrings.get().storagePermissionRequiredToast, Toast.LENGTH_LONG).show()
        }
    }

    // File Picker for Standalone Launcher - Supports single or multiple files (PBI 8.3 & PBI 7.1)
    private var filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            showSheetFor(uris) { uri, name ->
                contentResolver.getType(uri) ?: getExplicitMimeType(name.substringAfterLast('.', ""))
            }
        }
    }

    private var selectionJob: Job? = null
    private var refreshJob: Job? = null

    /**
     * Opens the converter sheet once the files' name, type and size are read. That costs two
     * provider queries per file (about 1 s for 40 photos), so it runs off the main thread.
     */
    private fun showSheetFor(uris: List<Uri>, mimeFor: (Uri, String) -> String) {
        selectionJob?.cancel()
        selectionJob = lifecycleScope.launch {
            selectedFilesForConversion = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val name = getFileNameFromUri(this@MainActivity, uri)
                    SelectedFileItem(uri, name, mimeFor(uri, name), getFileSizeFromUri(this@MainActivity, uri))
                }
            }
            showBottomSheet = true
        }
    }

    private var selectedFilesForConversion by mutableStateOf<List<SelectedFileItem>>(emptyList())
    private var showBottomSheet by mutableStateOf(false)

    companion object {
        var isAppInForeground = false
    }

    private var isStandaloneMode by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppStrings.init(this)
        // Leftovers from a conversion killed mid-way (only safe while nothing is queued)
        if (!ConversionQueueManager.hasActiveOrWaitingJobs()) OutputManager.cleanStaleTempFiles(this)
        ensureLegacyStoragePermission()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val isInitialStandalone = intent.action == Intent.ACTION_MAIN

        if (!isInitialStandalone && intent.action == Intent.ACTION_SEND) {
            // IntentCompat skips the type-safe getParcelableExtra on Android 13, where it is buggy
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { isSharedContentUri(it) }

            if (uri != null) {
                isStandaloneMode = false
                showSheetFor(listOf(uri)) { u, _ -> intent.type ?: contentResolver.getType(u) ?: "*/*" }
            } else {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    Toast.makeText(this, AppStrings.get().webUrlNotSupportedToast, Toast.LENGTH_LONG).show()
                }
                isStandaloneMode = true
            }
        } else if (!isInitialStandalone && intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris: ArrayList<Uri>? = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)

            val validUris = (if (!uris.isNullOrEmpty()) {
                uris.filterNotNull()
            } else {
                val clipData = intent.clipData
                if (clipData != null) {
                    (0 until clipData.itemCount).mapNotNull { clipData.getItemAt(it).uri }
                } else {
                    emptyList()
                }
            }).filter { isSharedContentUri(it) }

            if (validUris.isNotEmpty()) {
                isStandaloneMode = false
                showSheetFor(validUris) { u, _ -> contentResolver.getType(u) ?: "*/*" }
            } else {
                Toast.makeText(this, AppStrings.get().noFilesFromShareToast, Toast.LENGTH_SHORT).show()
                isStandaloneMode = true
            }
        } else {
            isStandaloneMode = true
        }

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val prefs = remember { MorphPrefs(context) }

            // Reactive Theme & Language State
            var currentThemeOption by remember { mutableStateOf(prefs.themeOption) }
            var currentLanguage by remember { mutableStateOf(prefs.appLanguage) }
            val strings = AppStrings.get(currentLanguage)

            //batterry setting
            var isBatteryUnrestricted by remember { mutableStateOf(checkBatteryOptimizationDisabled(context)) }

            // Dual-State Navigation: Standalone vs Share Mode
            var currentTab by remember { mutableStateOf(MorphTab.Home) }
            var isOnboarding by remember { mutableStateOf(!prefs.onboardingDone) }
            var showProgressScreen by remember { mutableStateOf(false) }

            // Double-back-to-exit (PBI: Back Navigation)
            var backPressedOnce by remember { mutableStateOf(false) }

            // BackHandler: tab non-Home → kembali ke Home; Home → double back exit
            BackHandler(enabled = isStandaloneMode) {
                if (currentTab != MorphTab.Home) {
                    currentTab = MorphTab.Home
                    backPressedOnce = false
                } else {
                    if (backPressedOnce) {
                        (context as? ComponentActivity)?.finish()
                    } else {
                        backPressedOnce = true
                        Toast.makeText(context, strings.pressBackAgainToExit, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Reset flag double-back setelah 2 detik
            LaunchedEffect(backPressedOnce) {
                if (backPressedOnce) {
                    kotlinx.coroutines.delay(2000)
                    backPressedOnce = false
                }
            }

            // Converted files state
            // History is loaded off the main thread: SAF entries cost one provider query each
            var morphFiles by remember { mutableStateOf<List<MorphOutputFile>>(emptyList()) }
            var isRefreshing by remember { mutableStateOf(true) }
            val uiScope = rememberCoroutineScope()
            var hasNotificationPermission by remember { mutableStateOf(checkNotifPermission(context)) }

            // Only the newest refresh may write the list: an older, slower listing must not overwrite it
            val refreshFiles: () -> Unit = {
                refreshJob?.cancel()
                refreshJob = uiScope.launch {
                    isRefreshing = true
                    morphFiles = withContext(Dispatchers.IO) { getMorphFiles(context) }
                    isRefreshing = false
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isAppInForeground = true
                            hasNotificationPermission = checkNotifPermission(context)
                            isBatteryUnrestricted = checkBatteryOptimizationDisabled(context)
                            refreshFiles()
                        }
                        Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                            isAppInForeground = false
                        }
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    isAppInForeground = false
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            MorphTheme(themeOption = currentThemeOption, language = currentLanguage) {
                val backgroundColor = if (isStandaloneMode) MaterialTheme.colorScheme.background else Color.Transparent

                // Standalone screens draw behind transparent system bars, with icon colour following the
                // app theme (not the system one). The share overlay keeps its own window untouched.
                val isDarkTheme = LocalMorphColors.current.isDark
                if (isStandaloneMode) {
                    LaunchedEffect(isDarkTheme) {
                        val barStyle = if (isDarkTheme) {
                            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        } else {
                            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                        }
                        // The legacy translucent theme leaves bar drawing to the system (solid black bars)
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (isStandaloneMode) {
                        val screen = when {
                            isOnboarding -> MorphScreen.Onboarding
                            showProgressScreen -> MorphScreen.Progress
                            else -> MorphScreen.Main
                        }
                        // Screen changes fade with a short rise; nothing slides across the whole width
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                (fadeIn(MorphMotion.slow()) + slideInVertically(MorphMotion.slow()) { it / 24 }) togetherWith
                                    fadeOut(MorphMotion.fast())
                            },
                            label = "screen"
                        ) { target ->
                            when (target) {
                                MorphScreen.Onboarding -> WelcomeScreen(
                                    onGetStarted = {
                                        prefs.onboardingDone = true
                                        isOnboarding = false
                                    }
                                )

                                MorphScreen.Progress -> ProgressScreen(
                                    onMinimize = { showProgressScreen = false },
                                    onFinish = {
                                        showProgressScreen = false
                                        refreshFiles()
                                    }, onViewResult = {
                                        showProgressScreen = false
                                        // Ambil file yang baru saja selesai dikonversi
                                        uiScope.launch {
                                            val lastOutput = ConversionQueueManager.queueState.value
                                                .lastOrNull { it.status == JobStatus.COMPLETED }?.outputFilePath
                                            val files = withContext(Dispatchers.IO) { getMorphFiles(context) }
                                            morphFiles = files
                                            // History order relies on lastModified, which some SAF providers report as 0
                                            val latestFile = withContext(Dispatchers.IO) { resolveOutputFile(context, lastOutput) }
                                                ?: files.firstOrNull()
                                            if (latestFile != null) {
                                                openFileDirectly(context, latestFile, strings)
                                            } else {
                                                Toast.makeText(context, strings.fileNotFoundToast, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )

                                MorphScreen.Main -> Scaffold(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    topBar = {
                                        // One bar for every tab: the wordmark on Home, the tab name elsewhere
                                        MorphTopBar(
                                            title = when (currentTab) {
                                                MorphTab.Home -> null
                                                MorphTab.Tools -> strings.tabTools
                                                MorphTab.Settings -> strings.tabSettings
                                            }
                                        )
                                    },
                                    bottomBar = {
                                        MorphBottomNavigation(
                                            items = listOf(
                                                MorphNavItem(strings.tabHome, Icons.Outlined.Home, Icons.Rounded.Home),
                                                MorphNavItem(strings.tabTools, Icons.Outlined.Handyman, Icons.Rounded.Handyman),
                                                MorphNavItem(strings.tabSettings, Icons.Outlined.Settings, Icons.Rounded.Settings)
                                            ),
                                            selectedIndex = currentTab.ordinal,
                                            onSelect = { index -> currentTab = MorphTab.entries[index] }
                                        )
                                    }
                                ) { innerPadding ->
                                    AnimatedContent(
                                        targetState = currentTab,
                                        modifier = Modifier.padding(innerPadding),
                                        transitionSpec = {
                                            fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                                                fadeOut(tween(140, easing = FastOutSlowInEasing))
                                        },
                                        label = "tab"
                                    ) { tab ->
                                        when (tab) {
                                            MorphTab.Home -> {
                                                HomeScreen(
                                                    morphFiles = morphFiles,
                                                    isRefreshing = isRefreshing,
                                                    hasNotificationPermission = hasNotificationPermission,
                                                    onRefresh = { refreshFiles() },
                                                    onPickFileClick = { filePickerLauncher.launch("*/*") },
                                                    onRequestPermission = { askNotificationPermission() },
                                                    onFileClick = { file -> openFileDirectly(context, file, strings) },
                                                    onShareFile = { file -> shareFileDirectly(context, file, strings) },
                                                    onDeleteFile = { file ->
                                                        if (!OutputManager.deleteOutputFile(context, file)) {
                                                            Toast.makeText(context, strings.deleteFileFailedToast, Toast.LENGTH_SHORT).show()
                                                        }
                                                        refreshFiles()
                                                    },
                                                    onRetryConversion = { retryUri, retryFormat ->
                                                        val serviceIntent = Intent(context, MorphService::class.java).apply {
                                                            putExtra("INPUT_URI", retryUri)
                                                            putExtra("TARGET_FORMAT", retryFormat)
                                                            putExtra("QUALITY_PRESET", prefs.qualityPreset)
                                                        }
                                                        context.startForegroundService(serviceIntent)
                                                    },
                                                    onBannerClick = { showProgressScreen = true }
                                                )

                                                // BottomSheet Picker Trigger
                                                if (showBottomSheet && selectedFilesForConversion.isNotEmpty()) {
                                                    BottomSheetConverter(
                                                        selectedFiles = selectedFilesForConversion,
                                                        defaultQuality = prefs.qualityPreset,
                                                        onDismiss = {
                                                            showBottomSheet = false
                                                            selectedFilesForConversion = emptyList()
                                                        },
                                                        onConvertStarted = {
                                                            showBottomSheet = false
                                                            selectedFilesForConversion = emptyList()
                                                            showProgressScreen = true
                                                        }
                                                    )
                                                }
                                            }

                                            MorphTab.Tools -> {
                                                ToolsScreen(
                                                    onPickFileForTool = { filePickerLauncher.launch("*/*") },
                                                    onBackClick = { currentTab = MorphTab.Home }
                                                )
                                            }

                                            MorphTab.Settings -> {
                                                SettingsScreen(
                                                    currentTheme = currentThemeOption,
                                                    onThemeChange = { newTheme ->
                                                        currentThemeOption = newTheme
                                                        prefs.themeOption = newTheme
                                                    },
                                                    onBackClick = { currentTab = MorphTab.Home },
                                                    currentLanguage = currentLanguage,
                                                    onLanguageChange = { newLang ->
                                                        currentLanguage = newLang
                                                        prefs.appLanguage = newLang
                                                        AppStrings.setLanguage(newLang)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                } else {
                        // --- LAYAR SHARE OVERLAY (Transparan) ---
                        if (showBottomSheet && selectedFilesForConversion.isNotEmpty()) {
                            LaunchedEffect(Unit) { askNotificationPermission() }

                            BottomSheetConverter(
                                selectedFiles = selectedFilesForConversion,
                                defaultQuality = prefs.qualityPreset,
                                onDismiss = {
                                    showBottomSheet = false
                                    selectedFilesForConversion = emptyList()
                                    finish() // Tutup overlay transparan
                                },
                                onConvertStarted = {
                                    showBottomSheet = false
                                    selectedFilesForConversion = emptyList()
                                    Toast.makeText(this@MainActivity, strings.conversionStartedBackgroundToast, Toast.LENGTH_SHORT).show()
                                    // PBI 8.2.3: Perilaku Tutup Otomatis
                                    if (prefs.closeAfterShare) {
                                        finish()
                                    } else {
                                        isStandaloneMode = true
                                        currentTab = MorphTab.Home
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Android 8–9 (API 26–28) write Downloads/Morph with direct File I/O and need
     * WRITE_EXTERNAL_STORAGE. Android 10 saves via MediaStore (no permission) but lists
     * history through file paths in legacy mode, which needs READ_EXTERNAL_STORAGE.
     */
    private fun legacyStoragePermission(): String? = when {
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> null
    }

    private fun ensureLegacyStoragePermission() {
        val permission = legacyStoragePermission() ?: return
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            storagePermissionLauncher.launch(permission)
        }
    }

    /** Only path back after a permanent denial: the permission switch in App info. */
    private fun showStorageSettingsDialog() {
        val strings = AppStrings.get()
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(strings.storagePermissionSettingsTitle)
            .setMessage(strings.storagePermissionSettingsMessage)
            .setPositiveButton(strings.btnOpenAppSettings) { _, _ -> openAppBatterySettings(this, strings) }
            .setNegativeButton(strings.btnCancel, null)
            .show()
    }
}

// --- UTILITY METHODS FOR FILE ACCESS & SHARING ---

/**
 * Share input must be a content:// URI. A file:// URI is read with Morph's own permissions, so
 * another app could point it at Morph's private files (prefs, cached user files) and have them
 * "converted" into public Downloads. Real senders use content URIs (FileProvider, MediaStore).
 */
fun isSharedContentUri(uri: Uri): Boolean = uri.scheme.equals(android.content.ContentResolver.SCHEME_CONTENT, ignoreCase = true)
fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name: String? = null
    try {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx != -1) name = c.getString(nameIdx)
            }
        }
    } catch (_: Exception) {}
    val base = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
    // The engine routes by extension: "IMG-2026" shared without ".jpg" would fall to FFmpeg and fail
    val mime = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
    return withExtensionFromMime(base, mime)
}

/** Appends the MIME type's extension when [name] has none (e.g. "IMG-2026" + image/jpeg -> "IMG-2026.jpg"). */
fun withExtensionFromMime(name: String, mimeType: String?): String {
    if (name.substringAfterLast('.', "").isNotEmpty() || mimeType.isNullOrBlank()) return name
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType.lowercase())
    return if (ext.isNullOrBlank()) name else "${name.trimEnd('.')}.$ext"
}

fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    var size = 0L
    try {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx != -1) size = c.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}
    return size
}

fun checkNotifPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}

fun getMorphFiles(context: Context): List<MorphOutputFile> = OutputManager.listOutputFiles(context)

/**
 * Turns a job's outputFilePath back into a history item: an absolute path (Downloads/Morph) or a
 * SAF document URI. Anything else (e.g. the "N pages extracted" message of PDF -> images) is null.
 */
fun resolveOutputFile(context: Context, outputPath: String?): MorphOutputFile? = when {
    outputPath == null -> null
    outputPath.startsWith("content://") -> Uri.parse(outputPath).let { MorphOutputFile(it, getFileNameFromUri(context, it), 0L, 0L) }
    outputPath.startsWith("/") -> File(outputPath).takeIf { it.isFile }
        ?.let { MorphOutputFile(Uri.fromFile(it), it.name, it.length(), it.lastModified(), it) }
    else -> null
}

fun getExplicitMimeType(extension: String): String =
    OutputManager.getMimeTypeForExtension(extension, fallback = "*/*")

/** content:// URI other apps can read: FileProvider for Downloads/Morph files, the SAF document URI otherwise. */
private fun shareableUri(context: Context, file: MorphOutputFile): Uri =
    file.file?.let { FileProvider.getUriForFile(context, "${context.packageName}.provider", it) } ?: file.uri

fun openFileDirectly(context: Context, file: MorphOutputFile, strings: Strings = AppStrings.get()) {
    try {
        val uri = shareableUri(context, file)
        val mimeType = getExplicitMimeType(file.extension)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } 
        val chooserIntent = Intent.createChooser(intent, strings.openWithTitle(file.extension.uppercase()))
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        Toast.makeText(context, strings.noAppToOpenToast(file.extension.uppercase()), Toast.LENGTH_SHORT).show()
    }
}

fun shareFileDirectly(context: Context, file: MorphOutputFile, strings: Strings = AppStrings.get()) {
    try {
        val uri = shareableUri(context, file)
        val mimeType = getExplicitMimeType(file.extension)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, strings.shareViaTitle))
    } catch (e: Exception) {
        Toast.makeText(context, strings.shareFailedToast, Toast.LENGTH_SHORT).show()
    }
}

fun checkBatteryOptimizationDisabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

fun openAppBatterySettings(context: Context, strings: Strings = AppStrings.get()) {
    try {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(context, strings.batterySettingsGuideToast, Toast.LENGTH_LONG).show()
        }
    }
}