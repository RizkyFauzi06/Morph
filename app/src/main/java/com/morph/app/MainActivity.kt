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
import androidx.compose.foundation.layout.Box
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

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
            val context = LocalContext.current
            val prefs = remember { MorphPrefs(context) }
            
            // Logika Deteksi Tema (PBI 8.2.2)
            val isDarkTheme = when (prefs.themeOption) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            MaterialTheme(
                // Jika Anda punya custom color scheme, taruh di sini. Untuk default, biarkan kosong agar otomatis mengikuti sistem M3.
                // colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme() 
            ) {
                val backgroundColor = if (isStandalone) MaterialTheme.colorScheme.background else Color.Transparent
                
                // --- STATE NAVIGASI SEDERHANA ---
                var currentScreen by remember { mutableStateOf("home") }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (isStandalone) {
                        if (currentScreen == "home") {
                            // Layar Utama
                            HomeScreen(
                                onPickFileClick = { standaloneFilePickerLauncher.launch("*/*") },
                                onRequestPermission = { askNotificationPermission() },
                                onSettingsClick = { currentScreen = "settings" } // LEMPAR FUNGSI KLIK
                            )
                            
                            // BottomSheet
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
                        } else if (currentScreen == "settings") {
                            // Layar Pengaturan
                            SettingsScreen(
                                onBackClick = { currentScreen = "home" }
                            )
                        }
                    } else {
                        // --- LAYAR SHARE (Tembus Pandang) ---
                        if (sharedUri != null) {
                            LaunchedEffect(Unit) { askNotificationPermission() }
                            
                            var showShareSheet by remember { mutableStateOf(true) }
                            if (showShareSheet) {
                                BottomSheetConverter(
                                    fileUri = sharedUri,
                                    fileName = fileName,
                                    mimeType = mimeType,
                                    onDismiss = { 
                                        showShareSheet = false
                                        // LOGIKA 8.2.3: Periksa apakah disuruh auto-close
                                        val prefs = MorphPrefs(this@MainActivity)
                                        if (prefs.closeAfterShare) {
                                            finish() 
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

// Ui Home Screen dan Izin
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onPickFileClick: () -> Unit,
    onRequestPermission: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var hasNotificationPermission by remember { mutableStateOf(checkNotifPermission(context)) }
    var morphFiles by remember { mutableStateOf(emptyList<File>()) }
    var isRefreshing by remember { mutableStateOf(false) }

    val engineState by MorphEngine.currentStatus.collectAsState()

    val refreshFiles = {
        isRefreshing = true
        morphFiles = getMorphFiles()
        isRefreshing = false
    }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { coroutineScope.launch { refreshFiles() } }
    )

    LaunchedEffect(engineState) {
        if (engineState is MorphEngine.ConvertState.Success) {
            refreshFiles()
            delay(1500)
            MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    MainActivity.isAppInForeground = true // Latar depan
                    hasNotificationPermission = checkNotifPermission(context)
                    refreshFiles()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    MainActivity.isAppInForeground = false // Masuk latar belakang
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { 
            MainActivity.isAppInForeground = false
            lifecycleOwner.lifecycle.removeObserver(observer) 
        }
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
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).pullRefresh(pullRefreshState)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),horizontalArrangement = Arrangement.End) 
            {
                IconButton(onClick = onSettingsClick) {Text("⚙️", style = MaterialTheme.typography.headlineMedium)}
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Morph",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary)
                // --- IN-APP PROGRESS UI (PBI 8.9 Tambah Tombol Batal) ---
                if (engineState is MorphEngine.ConvertState.Converting) {
                    val progress = (engineState as MorphEngine.ConvertState.Converting).progress
                    val format = (engineState as MorphEngine.ConvertState.Converting).format.uppercase()
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Memproses ke $format...", style = MaterialTheme.typography.labelLarge)
                                // Tombol Pembatalan
                                IconButton(onClick = { MorphEngine.cancelCurrentConversion() }, modifier = Modifier.size(24.dp)) {
                                    Text("✖", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$progress%", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                // --- KARTU ERROR & RETRY (PBI 8.1.4 & 8.1.10) ---
                if (engineState is MorphEngine.ConvertState.Error) {
                    val errorState = engineState as MorphEngine.ConvertState.Error
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Konversi Gagal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(errorState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle }) {
                                    Text("Tutup", color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                if (errorState.originalUri != null && errorState.targetFormat.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            MorphEngine.currentStatus.value = MorphEngine.ConvertState.Idle
                                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                                putExtra("INPUT_URI", errorState.originalUri)
                                                putExtra("TARGET_FORMAT", errorState.targetFormat)
                                            }
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent)
                                            else context.startService(serviceIntent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Text("Coba Lagi")
                                    }
                                }
                            }
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
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onPickFileClick, modifier = Modifier.size(160.dp), shape = androidx.compose.foundation.shape.CircleShape, elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+", style = MaterialTheme.typography.displayMedium)
                            Text("Pilih File")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("File Anda", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(morphFiles) { file ->
                            // PBI 8.1.7 & 8.1.9: STATE UNTUK MENU DROPDOWN & DIALOG HAPUS
                            var expanded by remember { mutableStateOf(false) }
                            var showDeleteDialog by remember { mutableStateOf(false) }
                            if (showDeleteDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteDialog = false },
                                    title = { Text("Hapus File") },
                                    text = { Text("Hapus ${file.name} secara permanen dari perangkat ini?") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            file.delete()
                                            showDeleteDialog = false
                                            refreshFiles()
                                        }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteDialog = false }) { Text("Batal") }
                                    }
                                )
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { openFileDirectly(context, file) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                ListItem(
                                    headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(formatFileSize(file.length())) },
                                    trailingContent = { 
                                        Box {
                                            // Tombol Titik Tiga (Dropdown)
                                            IconButton(onClick = { expanded = true }) {
                                                Text("⋮", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Bagikan") },
                                                    onClick = {
                                                        expanded = false
                                                        shareFileDirectly(context, file)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        expanded = false
                                                        showDeleteDialog = true
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
            PullRefreshIndicator(refreshing = isRefreshing, state = pullRefreshState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}



//UI Bottom Sheet
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetConverter(fileUri: Uri, fileName: String, mimeType: String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    var fileSizeStr by remember { mutableStateOf("Menghitung ukuran...") }
    LaunchedEffect(fileUri) {
        val cursor = context.contentResolver.query(fileUri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex != -1) {
                val sizeInBytes = cursor.getLong(sizeIndex)
                fileSizeStr = formatFileSize(sizeInBytes)
            }
            cursor.close()
        } else {
            fileSizeStr = "Ukuran tidak diketahui"
        }
    }
    
    // --- UX BARU: State Kualitas Lokal ---
    var selectedQuality by remember { mutableStateOf(0) } // 0: Tinggi, 1: Sedang, 2: Rendah

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ubah Format File", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            
            Text(
                text = "$fileName • $fileSizeStr", 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            // --- UX BARU: UI Pemilih Kualitas Elegan ---
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
                listOf("Tinggi", "Sedang", "Rendah").forEachIndexed { index, title ->
                    val isSelected = selectedQuality == index
                    Surface(
                        modifier = Modifier.clickable { selectedQuality = index }.padding(horizontal = 4.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Text(
                            text = title, 
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                            style = MaterialTheme.typography.labelMedium, 
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val formatsToOptions = getTargetFormats(fileName, mimeType)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                formatsToOptions.forEach { format ->
                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                putExtra("INPUT_URI", fileUri)
                                putExtra("TARGET_FORMAT", format)
                                putExtra("QUALITY_PRESET", selectedQuality) // KIRIM KUALITAS KE SERVICE
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(format.uppercase())
                    }
                }
            }
        }
    }
}

// UI Setting and many more
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MorphPrefs(context) }
    
    // State untuk memicu re-render UI saat diswitch
    var themeOption by remember { mutableStateOf(prefs.themeOption) }
    var keepName by remember { mutableStateOf(prefs.keepOriginalName) }
    var closeShare by remember { mutableStateOf(prefs.closeAfterShare) }
    var quality by remember { mutableStateOf(prefs.qualityPreset) }

    Scaffold(
        topBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBackClick) { Text("⬅ Kembali") }
                Spacer(modifier = Modifier.width(16.dp))
                Text("Pengaturan", style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            
            // PBI 8.2.1: Output Directory
            item {
                Text("Penyimpanan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lokasi Output File", style = MaterialTheme.typography.bodyLarge)
                        Text("Downloads/Morph", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("(Fitur ubah folder akan hadir di pembaruan mendatang)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PBI 8.2.2: Tema
            item {
                Text("Tampilan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tema Aplikasi", style = MaterialTheme.typography.bodyLarge)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { themeOption = 0; prefs.themeOption = 0 }) { Text("Sistem", color = if (themeOption == 0) MaterialTheme.colorScheme.primary else Color.Gray) }
                            TextButton(onClick = { themeOption = 1; prefs.themeOption = 1 }) { Text("Terang", color = if (themeOption == 1) MaterialTheme.colorScheme.primary else Color.Gray) }
                            TextButton(onClick = { themeOption = 2; prefs.themeOption = 2 }) { Text("Gelap", color = if (themeOption == 2) MaterialTheme.colorScheme.primary else Color.Gray) }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // PBI 8.2.3, 8.2.4, 8.2.5: Behavior & Toggles
            item {
                Text("Perilaku Aplikasi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pertahankan Nama Asli", style = MaterialTheme.typography.bodyLarge)
                                Text("Jangan tambah angka acak jika tidak bentrok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = keepName, onCheckedChange = { keepName = it; prefs.keepOriginalName = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tutup Otomatis", style = MaterialTheme.typography.bodyLarge)
                                Text("Tutup Morph setelah memilih format via Share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = closeShare, onCheckedChange = { closeShare = it; prefs.closeAfterShare = it })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PBI 8.2.6 & 8.2.8: Cleanup & About
            item {
                Text("Sistem", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Morph v1.8 (The Share MVP)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Universal File Converter by Rizki Fauzi", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = { 
                                val cacheDir = context.cacheDir
                                cacheDir.deleteRecursively()
                                Toast.makeText(context, "File sampah sementara berhasil dihapus!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Bersihkan File Sementara (Cache)")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

fun getTargetFormats(fileName: String, mimeType: String): List<String> {
    val originalExt = fileName.substringAfterLast('.', "").lowercase()
    val availableFormats = mutableListOf<String>()

    // Deteksi apakah file ini masuk akal untuk dikompresi ke format yang sama
    val isCompressible = mimeType.startsWith("video/") || 
                         mimeType.startsWith("image/") || 
                         mimeType.startsWith("audio/") || 
                         mimeType == "application/pdf" || 
                         originalExt == "pdf"

    // Tentukan daftar format tujuan berdasarkan tipe file
    if (mimeType.startsWith("video/")) availableFormats.addAll(listOf("mp4", "mkv", "avi", "gif", "mp3", "m4a"))
    else if (mimeType.startsWith("image/")) availableFormats.addAll(listOf("png", "jpg", "webp"))
    else if (mimeType.startsWith("audio/")) availableFormats.addAll(listOf("mp3", "m4a", "wav"))
    else if (mimeType == "application/pdf" || originalExt == "pdf") availableFormats.addAll(listOf("pdf", "jpg", "png")) // PERSIAPAN PDF
    else availableFormats.addAll(listOf("mp4", "png", "mp3")) // Default fallback

    // LOGIKA PINTAR: Hapus format asal JIKA BUKAN file yang bisa dikompresi
    if (!isCompressible) {
        availableFormats.remove(originalExt)
    } else {
        // Jika bisa dikompresi, pastikan format aslinya ada di urutan teratas agar mudah diklik
        if (!availableFormats.contains(originalExt) && originalExt.isNotEmpty()) {
            availableFormats.add(0, originalExt)
        }
    }

    return availableFormats.distinct()
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

fun shareFileDirectly(context: android.content.Context, file: File) {
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

//Format Size Math
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}

// --- PENGELOLA PENGATURAN (PBI 8.2) ---
class MorphPrefs(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("morph_settings", android.content.Context.MODE_PRIVATE)

    var themeOption: Int // 0: System, 1: Light, 2: Dark
        get() = prefs.getInt("theme", 0)
        set(value) = prefs.edit().putInt("theme", value).apply()

    var showNotifications: Boolean
        get() = prefs.getBoolean("notif", true)
        set(value) = prefs.edit().putBoolean("notif", value).apply()

    var keepOriginalName: Boolean
        get() = prefs.getBoolean("keep_name", true)
        set(value) = prefs.edit().putBoolean("keep_name", value).apply()

    var closeAfterShare: Boolean
        get() = prefs.getBoolean("close_share", true)
        set(value) = prefs.edit().putBoolean("close_share", value).apply()

    var qualityPreset: Int // 0: High, 1: Medium, 2: Low (Disiapkan untuk Sprint 3)
        get() = prefs.getInt("quality", 0)
        set(value) = prefs.edit().putInt("quality", value).apply()
}