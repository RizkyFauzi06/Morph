package com.morph.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.morph.app.ui.screens.BottomSheetConverter
import com.morph.app.ui.screens.HomeScreen
import com.morph.app.ui.screens.SettingsScreen
import com.morph.app.ui.screens.ToolsScreen
import com.morph.app.ui.theme.MorphTheme
import java.io.File

enum class MorphTab {
    Home, Tools, Settings
}

class MainActivity : ComponentActivity() {

    // Notification permission launcher (PBI 7.5)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Izin notifikasi diperlukan agar konversi latar belakang berjalan optimal.", Toast.LENGTH_LONG).show()
        }
    }

    // File Picker for Standalone Launcher (PBI 7.1)
    private var filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fileUriFromPicker = uri
            showBottomSheetFromPicker = true
        }
    }

    private var fileUriFromPicker by mutableStateOf<Uri?>(null)
    private var showBottomSheetFromPicker by mutableStateOf(false)

    companion object {
        var isAppInForeground = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val isInitialStandalone = intent.action == Intent.ACTION_MAIN
        var sharedUri: Uri? = null
        var fileName = "Menunggu file..."
        var mimeType = "*/*"

        if (!isInitialStandalone && intent.action == Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            if (sharedUri != null) {
                fileName = sharedUri.lastPathSegment ?: "File"
                mimeType = contentResolver.getType(sharedUri) ?: "*/*"
            }
        }

        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val prefs = remember { MorphPrefs(context) }

            // Reactive Theme State (PBI 8.2.2)
            var currentThemeOption by remember { mutableStateOf(prefs.themeOption) }

            // Dual-State Navigation: Standalone vs Share Mode
            var isStandaloneMode by remember { mutableStateOf(isInitialStandalone) }
            var currentTab by remember { mutableStateOf(MorphTab.Home) }

            // Converted files state
            var morphFiles by remember { mutableStateOf(getMorphFiles()) }
            var isRefreshing by remember { mutableStateOf(false) }
            var hasNotificationPermission by remember { mutableStateOf(checkNotifPermission(context)) }

            val refreshFiles = {
                isRefreshing = true
                morphFiles = getMorphFiles()
                isRefreshing = false
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> {
                            isAppInForeground = true
                            hasNotificationPermission = checkNotifPermission(context)
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

            MorphTheme(themeOption = currentThemeOption) {
                val backgroundColor = if (isStandaloneMode) MaterialTheme.colorScheme.background else Color.Transparent

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (isStandaloneMode) {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 6.dp
                                ) {
                                    NavigationBarItem(
                                        selected = currentTab == MorphTab.Home,
                                        onClick = { currentTab = MorphTab.Home },
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Beranda") },
                                        label = { Text("Beranda", fontWeight = if (currentTab == MorphTab.Home) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == MorphTab.Tools,
                                        onClick = { currentTab = MorphTab.Tools },
                                        icon = { Icon(Icons.Default.Handyman, contentDescription = "Alat") },
                                        label = { Text("Alat", fontWeight = if (currentTab == MorphTab.Tools) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                    NavigationBarItem(
                                        selected = currentTab == MorphTab.Settings,
                                        onClick = { currentTab = MorphTab.Settings },
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Pengaturan") },
                                        label = { Text("Setelan", fontWeight = if (currentTab == MorphTab.Settings) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (currentTab) {
                                    MorphTab.Home -> {
                                        HomeScreen(
                                            morphFiles = morphFiles,
                                            isRefreshing = isRefreshing,
                                            hasNotificationPermission = hasNotificationPermission,
                                            onRefresh = { refreshFiles() },
                                            onPickFileClick = { filePickerLauncher.launch("*/*") },
                                            onRequestPermission = { askNotificationPermission() },
                                            onFileClick = { file -> openFileDirectly(context, file) },
                                            onShareFile = { file -> shareFileDirectly(context, file) },
                                            onDeleteFile = { file ->
                                                file.delete()
                                                refreshFiles()
                                            },
                                            onRetryConversion = { retryUri, retryFormat ->
                                                val serviceIntent = Intent(context, MorphService::class.java).apply {
                                                    putExtra("INPUT_URI", retryUri)
                                                    putExtra("TARGET_FORMAT", retryFormat)
                                                    putExtra("QUALITY_PRESET", prefs.qualityPreset)
                                                }
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                    context.startForegroundService(serviceIntent)
                                                } else {
                                                    context.startService(serviceIntent)
                                                }
                                            }
                                        )

                                        // BottomSheet Picker Trigger
                                        if (showBottomSheetFromPicker && fileUriFromPicker != null) {
                                            val pickerFileName = fileUriFromPicker?.lastPathSegment ?: "File Terpilih"
                                            val pickerMimeType = contentResolver.getType(fileUriFromPicker!!) ?: "*/*"

                                            BottomSheetConverter(
                                                fileUri = fileUriFromPicker!!,
                                                fileName = pickerFileName,
                                                mimeType = pickerMimeType,
                                                defaultQuality = prefs.qualityPreset,
                                                onDismiss = { showBottomSheetFromPicker = false },
                                                onConvertStarted = { showBottomSheetFromPicker = false }
                                            )
                                        }
                                    }

                                    MorphTab.Tools -> {
                                        ToolsScreen(
                                            onPickFileForTool = { filePickerLauncher.launch("*/*") }
                                        )
                                    }

                                    MorphTab.Settings -> {
                                        SettingsScreen(
                                            currentTheme = currentThemeOption,
                                            onThemeChange = { newTheme ->
                                                currentThemeOption = newTheme
                                                prefs.themeOption = newTheme
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // --- LAYAR SHARE OVERLAY (Transparan) ---
                        if (sharedUri != null) {
                            LaunchedEffect(Unit) { askNotificationPermission() }

                            var showShareSheet by remember { mutableStateOf(true) }
                            if (showShareSheet) {
                                BottomSheetConverter(
                                    fileUri = sharedUri,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    defaultQuality = prefs.qualityPreset,
                                    onDismiss = {
                                        showShareSheet = false
                                        finish() // Tutup overlay transparan
                                    },
                                    onConvertStarted = {
                                        showShareSheet = false
                                        Toast.makeText(this@MainActivity, "Konversi dimulai di latar belakang...", Toast.LENGTH_SHORT).show()
                                        // PBI 8.2.3: Perilaku Tutup Otomatis
                                        if (prefs.closeAfterShare) {
                                            finish()
                                        } else {
                                            isStandaloneMode = true
                                        }
                                    }
                                )
                            }
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
}

// --- UTILITY METHODS FOR FILE ACCESS & SHARING ---
fun checkNotifPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}

fun getMorphFiles(): List<File> {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val morphDir = File(downloadsDir, "Morph")
    return if (morphDir.exists() && morphDir.isDirectory) {
        morphDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    } else {
        emptyList()
    }
}

fun openFileDirectly(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka ${file.extension.uppercase()}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFileDirectly(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan via..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membagikan file", Toast.LENGTH_SHORT).show()
    }
}
