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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {

    // Launcher untuk minta izin notifikasi (PBI 7.5)
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
        val isInitialStandalone = intent.action == Intent.ACTION_MAIN
        var sharedUri: Uri? = null
        var fileName = "Menunggu file..."
        var mimeType = "*/*"

        if (!isInitialStandalone) {
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
            
            // PBI 8.2.2: Reactive Theme State (Sistem / Terang / Gelap)
            var currentThemeOption by remember { mutableStateOf(prefs.themeOption) }
            val isDarkTheme = when (currentThemeOption) {
                1 -> false // Terang
                2 -> true  // Gelap
                else -> isSystemInDarkTheme() // Ikuti Sistem
            }

            val appColorScheme = if (isDarkTheme) {
                darkColorScheme(
                    primary = Color(0xFFA5B4FC),
                    onPrimary = Color(0xFF1E1B4B),
                    primaryContainer = Color(0xFF3730A3),
                    onPrimaryContainer = Color(0xFFE0E7FF),
                    secondary = Color(0xFFC4B5FD),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B),
                    surfaceVariant = Color(0xFF334155),
                    onSurface = Color(0xFFF1F5F9),
                    onSurfaceVariant = Color(0xFF94A3B8)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF4F46E5),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEEF2FF),
                    onPrimaryContainer = Color(0xFF312E81),
                    secondary = Color(0xFF7C3AED),
                    background = Color(0xFFF8FAFC),
                    surface = Color.White,
                    surfaceVariant = Color(0xFFF1F5F9),
                    onSurface = Color(0xFF0F172A),
                    onSurfaceVariant = Color(0xFF64748B)
                )
            }

            // PBI 8.2.3: Perilaku Dual-State & Tutup Otomatis
            var isStandaloneMode by remember { mutableStateOf(isInitialStandalone) }

            MaterialTheme(colorScheme = appColorScheme) {
                val backgroundColor = if (isStandaloneMode) MaterialTheme.colorScheme.background else Color.Transparent
                var currentScreen by remember { mutableStateOf("home") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    if (isStandaloneMode) {
                        if (currentScreen == "home") {
                            HomeScreen(
                                onPickFileClick = { standaloneFilePickerLauncher.launch("*/*") },
                                onRequestPermission = { askNotificationPermission() },
                                onSettingsClick = { currentScreen = "settings" }
                            )
                            
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
                        } else if (currentScreen == "settings") {
                            SettingsScreen(
                                currentTheme = currentThemeOption,
                                onThemeChange = { newTheme ->
                                    currentThemeOption = newTheme
                                    prefs.themeOption = newTheme
                                },
                                onBackClick = { currentScreen = "home" }
                            )
                        }
                    } else {
                        // --- LAYAR SHARE (Transparan) ---
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
                                        finish() // Batalkan dan tutup aktivitas transparan
                                    },
                                    onConvertStarted = {
                                        showShareSheet = false
                                        Toast.makeText(this@MainActivity, "Konversi dimulai di latar belakang...", Toast.LENGTH_SHORT).show()
                                        // PBI 8.2.3: Tutup Otomatis kembali ke aplikasi asal jika aktif
                                        if (prefs.closeAfterShare) {
                                            finish()
                                        } else {
                                            // Jika switch mati, tetap buka Markas Utama untuk melihat progress
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

// --- PBI 10: OFFICIAL MORPH LOGO COMPOSABLE ---
@Composable
fun MorphLogo(modifier: Modifier = Modifier.size(56.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val radius = minOf(w, h) / 2f
        val center = Offset(w / 2f, h / 2f)

        // Vibrant Gradient Circle
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899)),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            radius = radius,
            center = center
        )

        // Elegant Dynamic Morph Curve
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.64f)
            cubicTo(
                w * 0.28f, h * 0.64f,
                w * 0.34f, h * 0.35f,
                w * 0.44f, h * 0.35f
            )
            cubicTo(
                w * 0.52f, h * 0.35f,
                w * 0.48f, h * 0.62f,
                w * 0.56f, h * 0.62f
            )
            cubicTo(
                w * 0.63f, h * 0.62f,
                w * 0.70f, h * 0.40f,
                w * 0.70f, h * 0.40f
            )
        }
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(
                width = w * 0.08f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Glowing Accent Dot
        drawCircle(
            color = Color.White,
            radius = w * 0.045f,
            center = Offset(w * 0.50f, h * 0.27f)
        )
    }
}

// --- UI HOME SCREEN & HISTORY (MARKAS UTAMA) ---
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
                    MainActivity.isAppInForeground = true
                    hasNotificationPermission = checkNotifPermission(context)
                    refreshFiles()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    MainActivity.isAppInForeground = false
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
                FloatingActionButton(
                    onClick = onPickFileClick, 
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Pilih File Baru")
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
                // Top Bar with Settings
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings, 
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Official Logo & Branding Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    MorphLogo(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Morph",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Universal File Converter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // --- IN-APP PROGRESS UI DENGAN TOMBOL BATAL (PBI 8.8 & 8.9) ---
                if (engineState is MorphEngine.ConvertState.Converting) {
                    val progress = (engineState as MorphEngine.ConvertState.Converting).progress
                    val format = (engineState as MorphEngine.ConvertState.Converting).format.uppercase()
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(), 
                                horizontalArrangement = Arrangement.SpaceBetween, 
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Memproses ke $format...", style = MaterialTheme.typography.labelLarge)
                                IconButton(
                                    onClick = { MorphEngine.cancelCurrentConversion() }, 
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close, 
                                        contentDescription = "Batalkan", 
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f }, 
                                modifier = Modifier.fillMaxWidth().height(8.dp), 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$progress%", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // --- KARTU ERROR & RETRY ---
                if (engineState is MorphEngine.ConvertState.Error) {
                    val errorState = engineState as MorphEngine.ConvertState.Error
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), 
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Konversi Gagal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.onErrorContainer, 
                                            contentColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Coba Lagi")
                                    }
                                }
                            }
                        }
                    }
                }

                // Edukasi Izin Notifikasi (PBI 7.5)
                if (!hasNotificationPermission) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), 
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Izin Notifikasi Diperlukan", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Morph butuh izin notifikasi agar sistem tidak mematikan konversi file besar Anda di latar belakang.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onRequestPermission() }, 
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Text("Izinkan Notifikasi")
                            }
                        }
                    }
                }

                // Riwayat File (Empty vs Populated)
                if (morphFiles.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onPickFileClick, 
                        modifier = Modifier.size(150.dp), 
                        shape = CircleShape, 
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Pilih File")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text("File Anda (${morphFiles.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(morphFiles) { file ->
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
                                    headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(formatFileSize(file.length())) },
                                    trailingContent = { 
                                        Box {
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "Pilihan Menu", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                                DropdownMenuItem(
                                                    text = { Text("Bagikan") },
                                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        shareFileDirectly(context, file)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
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

// --- UI BOTTOM SHEET CONVERTER DENGAN LOSSLESS COPY & PARAMETER KUALITAS ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomSheetConverter(
    fileUri: Uri, 
    fileName: String, 
    mimeType: String, 
    defaultQuality: Int,
    onDismiss: () -> Unit,
    onConvertStarted: () -> Unit
) {
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
    
    // PBI 11: Kualitas Default mengikuti Pengaturan pengguna
    var selectedQuality by remember { mutableStateOf(defaultQuality) }
    val formatsToOptions = remember(fileName, mimeType) { getTargetFormats(fileName, mimeType) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Ubah Format File", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$fileName • $fileSizeStr", 
                style = MaterialTheme.typography.bodyMedium, 
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )

            // PBI 11: Pemilih Kualitas & Stream Copy
            Spacer(modifier = Modifier.height(16.dp))
            Text("Kualitas & Mode Output", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            val qualityOptions = listOf(
                Pair("Tinggi", 0),
                Pair("Sedang", 1),
                Pair("Rendah", 2),
                Pair("⚡ Instan (Lossless)", 3)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), 
                horizontalArrangement = Arrangement.Center
            ) {
                qualityOptions.forEach { (title, index) ->
                    val isSelected = selectedQuality == index
                    Surface(
                        modifier = Modifier.clickable { selectedQuality = index }.padding(horizontal = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Text(
                            text = title, 
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), 
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Keterangan UX Kualitas Dinamis
            val qualityHint = when (selectedQuality) {
                0 -> "Kualitas penuh (Bitrate 100% dipertahankan)"
                1 -> "Kompresi seimbang (Ukuran berkurang ~50%)"
                2 -> "Kompresi agresif (Resolusi 720p / ukuran hemat)"
                3 -> "Salin stream tanpa encode ulang. Super instan & kualitas 100% asli"
                else -> ""
            }
            Text(
                text = qualityHint,
                style = MaterialTheme.typography.labelSmall,
                color = if (selectedQuality == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Pilih Format Tujuan:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                formatsToOptions.forEach { format ->
                    val isPdfCompress = format.lowercase() == "pdf" && (fileName.endsWith(".pdf", ignoreCase = true) || mimeType == "application/pdf")
                    val buttonLabel = if (isPdfCompress) "KOMPRES PDF" else format.uppercase()

                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                putExtra("INPUT_URI", fileUri)
                                putExtra("TARGET_FORMAT", format)
                                putExtra("QUALITY_PRESET", selectedQuality)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            onConvertStarted()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(buttonLabel, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- UI SETTINGS SCREEN (PENGATURAN) ---
@Composable
fun SettingsScreen(
    currentTheme: Int,
    onThemeChange: (Int) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { MorphPrefs(context) }
    
    var keepName by remember { mutableStateOf(prefs.keepOriginalName) }
    var closeShare by remember { mutableStateOf(prefs.closeAfterShare) }
    var qualityPreset by remember { mutableStateOf(prefs.qualityPreset) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            
            // PBI 8.2.2: Tema Aplikasi (Sistem / Terang / Gelap)
            item {
                Text("Tampilan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Tema Aplikasi", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            FilterChip(
                                selected = currentTheme == 0,
                                onClick = { onThemeChange(0) },
                                label = { Text("Sistem") }
                            )
                            FilterChip(
                                selected = currentTheme == 1,
                                onClick = { onThemeChange(1) },
                                label = { Text("Terang") }
                            )
                            FilterChip(
                                selected = currentTheme == 2,
                                onClick = { onThemeChange(2) },
                                label = { Text("Gelap") }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PBI 11 & PBI 8.2.7: Kualitas Konversi Default
            item {
                Text("Mesin Konversi & Kualitas", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Kualitas Output Default", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Parameter awal saat BottomSheet konversi terbuka", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                Pair("Tinggi", 0),
                                Pair("Sedang", 1),
                                Pair("Rendah", 2),
                                Pair("Instan", 3)
                            ).forEach { (title, idx) ->
                                FilterChip(
                                    selected = qualityPreset == idx,
                                    onClick = { 
                                        qualityPreset = idx
                                        prefs.qualityPreset = idx
                                    },
                                    label = { Text(title) }
                                )
                            }
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp), 
                            horizontalArrangement = Arrangement.SpaceBetween, 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pertahankan Nama Asli", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Hindari penambahan suffix jika tidak ada file bentrok", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = keepName, onCheckedChange = { keepName = it; prefs.keepOriginalName = it })
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp), 
                            horizontalArrangement = Arrangement.SpaceBetween, 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tutup Otomatis (Share)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Kembali ke Galeri/Aplikasi asal setelah memilih format via Share", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = closeShare, onCheckedChange = { closeShare = it; prefs.closeAfterShare = it })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PBI 8.2.1: Lokasi Penyimpanan
            item {
                Text("Penyimpanan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Lokasi Output File", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Downloads/Morph", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("File tersimpan rapi sesuai standar Scoped Storage Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PBI 8.2.6 & 8.2.8: Cleanup & About Logo
            item {
                Text("Sistem", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MorphLogo(modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Morph v2.0 (Sprint 3)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("Universal File Converter by Rizki Fauzi", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                val cacheDir = context.cacheDir
                                cacheDir.deleteRecursively()
                                Toast.makeText(context, "File cache sementara berhasil dibersihkan!", Toast.LENGTH_SHORT).show()
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

// --- PEMETAAN FORMAT CERDAS (PBI 2.5, 11, 13) ---
fun getTargetFormats(fileName: String, mimeType: String): List<String> {
    val originalExt = fileName.substringAfterLast('.', "").lowercase()
    val availableFormats = mutableListOf<String>()

    val isCompressible = mimeType.startsWith("video/") || 
                         mimeType.startsWith("image/") || 
                         mimeType.startsWith("audio/") || 
                         mimeType == "application/pdf" || 
                         originalExt == "pdf"

    if (mimeType.startsWith("video/")) {
        availableFormats.addAll(listOf("mp4", "mkv", "avi", "gif", "mp3", "m4a"))
    } else if (mimeType.startsWith("image/")) {
        availableFormats.addAll(listOf("png", "jpg", "webp", "pdf"))
    } else if (mimeType.startsWith("audio/")) {
        availableFormats.addAll(listOf("mp3", "m4a", "wav"))
    } else if (mimeType == "application/pdf" || originalExt == "pdf") {
        availableFormats.addAll(listOf("pdf", "jpg", "png"))
    } else {
        availableFormats.addAll(listOf("mp4", "png", "mp3", "pdf"))
    }

    if (!isCompressible) {
        availableFormats.remove(originalExt)
    } else {
        if (!availableFormats.contains(originalExt) && originalExt.isNotEmpty()) {
            availableFormats.add(0, originalExt)
        }
    }

    return availableFormats.distinct()
}

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

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
}

// --- PENGELOLA PENGATURAN (PBI 8.2) ---
class MorphPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("morph_settings", Context.MODE_PRIVATE)

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

    var qualityPreset: Int // 0: Tinggi, 1: Sedang, 2: Rendah, 3: Instan (Lossless)
        get() = prefs.getInt("quality", 0)
        set(value) = prefs.edit().putInt("quality", value).apply()
}
