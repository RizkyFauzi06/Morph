package com.morph.app
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    // Launcher untuk minta izin
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Izin ditolak. Morph tidak bisa berjalan optimal di latar belakang.", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher untuk File Picker dari Markas Utama (PBI 7.1)
    private var standaloneFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            fileUriFromPicker = uri
            showBottomSheetFromPicker = true
        }
    }

    // State Global untuk Markas Utama
    private var fileUriFromPicker by mutableStateOf<Uri?>(null)
    private var showBottomSheetFromPicker by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        // DETEKSI LOGIKA: Apakah dibuka dari Launcher (Ikon) atau Share?
        val isStandalone = intent.action == Intent.ACTION_MAIN
        
        var sharedUri: Uri? = null
        var fileName = "Menunggu file..."
        var mimeType = "*/*"

        if (!isStandalone) {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    if (sharedUri != null) {
                        fileName = sharedUri.lastPathSegment ?: "File"
                        mimeType = contentResolver.getType(sharedUri) ?: "*/*"
                    }
                }
            }
        }

        setContent {
            MaterialTheme {
                // UI Dinamis: Solid jika Standalone, Transparan jika Share
                val backgroundColor = if (isStandalone) MaterialTheme.colorScheme.background else Color.Transparent
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (isStandalone) {
                        // --- LAYAR MARKAS UTAMA (PBI 7) ---
                        HomeScreen(
                            onPickFileClick = { standaloneFilePickerLauncher.launch("*/*") },
                            onRequestPermission = { askNotificationPermission() }
                        )
                        
                        // Memunculkan BottomSheet jika file dipilih dari Tombol Raksasa
                        if (showBottomSheetFromPicker && fileUriFromPicker != null) {
                            val pickerFileName = fileUriFromPicker?.lastPathSegment ?: "File Terpilih"
                            val pickerMimeType = contentResolver.getType(fileUriFromPicker!!) ?: "*/*"
                            
                            BottomSheetConverter(
                                fileUri = fileUriFromPicker!!,
                                fileName = pickerFileName,
                                mimeType = pickerMimeType,
                                onDismiss = { showBottomSheetFromPicker = false }
                            )
                        }
                    } else {
                        // --- LAYAR SHARE (Tembus Pandang) ---
                        if (sharedUri != null) {
                            // Langsung minta izin (jika belum) agar konversi tidak gagal
                            LaunchedEffect(Unit) { askNotificationPermission() }
                            
                            var showShareSheet by remember { mutableStateOf(true) }
                            if (showShareSheet) {
                                BottomSheetConverter(
                                    fileUri = sharedUri,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    onDismiss = { 
                                        showShareSheet = false
                                        finish() 
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

// Ui dan Izin
@Composable
fun HomeScreen(
    onPickFileClick: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasNotificationPermission by remember { mutableStateOf(checkNotifPermission(context)) }
    var morphFiles by remember { mutableStateOf(emptyList<File>()) }

    // 1. Dengarkan status dari Mesin FFmpeg secara Real-Time!
    val engineState by MorphEngine.currentStatus.collectAsState()

    // Fungsi Refresh Manual / Auto
    val refreshFiles = { morphFiles = getMorphFiles() }

    // 2. Trigger Auto-Update: Jika konversi sukses, refresh list, lalu kembalikan status ke Idle
    LaunchedEffect(engineState) {
        if (engineState is MorphEngine.ConvertState.Success) {
            refreshFiles()
            delay(2000) // Tahan sebentar jika ingin menampilkan pesan sukses, lalu reset
            MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = checkNotifPermission(context)
                refreshFiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        floatingActionButton = {
            if (morphFiles.isNotEmpty()) {
                FloatingActionButton(onClick = onPickFileClick, containerColor = MaterialTheme.colorScheme.primary) {
                    Text("+", style = MaterialTheme.typography.headlineMedium) 
                }
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text("Morph", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Text("Universal File Converter", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.height(24.dp))

            // --- IN-APP PROGRESS UI (Muncul saat proses jalan) ---
            if (engineState is MorphEngine.ConvertState.Converting) {
                val progress = (engineState as MorphEngine.ConvertState.Converting).progress
                val format = (engineState as MorphEngine.ConvertState.Converting).format.uppercase()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Memproses ke $format...", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = progress / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$progress%", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // ----------------------------------------------------

            // Edukasi Izin (PBI 7.5)
            if (!hasNotificationPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ Izin Notifikasi Diperlukan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Morph butuh izin ini agar sistem tidak mematikan proses konversi file besar Anda di latar belakang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onRequestPermission() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text("Izinkan Notifikasi")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // LOGIKA DINAMIS PBI 8.1 (Kosong vs Berisi)
           if (morphFiles.isEmpty()) {
                // UI JIKA KOSONG
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onPickFileClick,
                    modifier = Modifier.size(160.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", style = MaterialTheme.typography.displayMedium)
                        Text("Pilih File")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // UI JIKA BERISI & TOMBOL REFRESH MANUAL
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("File Anda", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    // Tombol Refresh Manual
                    TextButton(onClick = { refreshFiles() }) {
                        Text("⟳ Segarkan", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(morphFiles) { file ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { openFileDirectly(context, file) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { 
                                    val sizeKb = file.length() / 1024
                                    Text("$sizeKb KB") 
                                },
                                trailingContent = { Text(file.extension.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}



//UI
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetConverter(fileUri: Uri, fileName: String, mimeType: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ubah Format File", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                text = fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 24.dp)
            )

            val formatsToOptions = getTargetFormats(fileName, mimeType)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                formatsToOptions.forEach { format ->
                    ElevatedButton(
                        onClick = {
                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                putExtra("INPUT_URI", fileUri)
                                putExtra("TARGET_FORMAT", format)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                            else context.startService(serviceIntent)
                            
                            onDismiss()
                        },
                        modifier = Modifier.defaultMinSize(minWidth = 90.dp)
                    ) {
                        Text(format.uppercase())
                    }
                }
            }
        }
    }
}

fun getTargetFormats(fileName: String, mimeType: String): List<String> {
    val originalExt = fileName.substringAfterLast('.', "").lowercase()
    val availableFormats = mutableListOf<String>()

    if (mimeType.startsWith("video/")) availableFormats.addAll(listOf("mp4", "mkv", "avi", "gif", "mp3", "m4a"))
    else if (mimeType.startsWith("image/")) availableFormats.addAll(listOf("png", "jpg", "webp"))
    else if (mimeType.startsWith("audio/")) availableFormats.addAll(listOf("mp3", "m4a", "wav"))
    else availableFormats.addAll(listOf("mp4", "png", "mp3"))

    availableFormats.remove(originalExt)
    return availableFormats
}


fun checkNotifPermission(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
}

fun getMorphFiles(): List<File> {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val morphDir = File(downloadsDir, "Morph")
    return if (morphDir.exists() && morphDir.isDirectory) {
        // Ambil semua file yang bukan folder, lalu urutkan dari yang terbaru (descending)
        morphDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    } else {
        emptyList()
    }
}

fun openFileDirectly(context: android.content.Context, file: File) {
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