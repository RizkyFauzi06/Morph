package com.morph.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun handleIntent(intent: Intent) {
        var fileUri: Uri? = null
        var fileName = "Menunggu file..."
        var mimeType = "*/*"

        // Ekstraksi File URI
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                if (fileUri != null) {
                    fileName = fileUri.lastPathSegment ?: "File"
                    mimeType = contentResolver.getType(fileUri) ?: "*/*"
                }
            }
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    var showBottomSheet by remember { mutableStateOf(true) }
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    
                    // State untuk status konversi
                    var isConverting by remember { mutableStateOf(false) }
                    var resultMessage by remember { mutableStateOf<String?>(null) }
                    
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()

                    if (showBottomSheet) {
                        ModalBottomSheet(
                            onDismissRequest = {
                                showBottomSheet = false
                                finish()
                            },
                            sheetState = sheetState
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 48.dp, top = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isConverting) "Sedang Memproses..." else "Ubah Format File",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                if (isConverting) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                } else if (resultMessage != null) {
                                    Text(
                                        text = resultMessage!!,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                    Button(onClick = { 
                                        showBottomSheet = false
                                        finish() 
                                    }) {
                                        Text("Selesai & Tutup")
                                    }
                                } else if (fileUri != null) {
                                    // PANGGIL LOGIKA FORMAT DINAMIS
                                    val formatsToOptions = getTargetFormats(fileName, mimeType)
                                    
                                    // Karena kita bisa punya 5+ format, kita bungkus pakai FlowRow atau panggil FormatButtonsRow per 3 baris
                                    // Untuk UI MVP, kita potong 3 format paling atas saja agar muat rapi di BottomSheet
                                    FormatButtonsRow(
                                        formats = formatsToOptions.take(3),
                                        onFormatSelected = { format ->
                                            // FIRE AND FORGET UX
                                            // Lempar URI dan Target Format ke MorphService
                                            val serviceIntent = Intent(context, MorphService::class.java).apply {
                                                putExtra("INPUT_URI", fileUri)
                                                putExtra("TARGET_FORMAT", format)
                                            }
                                            
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                            // Tutup UI secara langsung, biarkan Service & Notifikasi bekerja!
                                            showBottomSheet = false
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
    }
}

@Composable
fun FormatButtonsRow(formats: List<String>, onFormatSelected: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        formats.forEach { format ->
            Button(
                onClick = { onFormatSelected(format) },
                modifier = Modifier.weight(1f)
            ) {
                Text(format.uppercase())
            }
        }
    }
}

// Fungsi untuk memetakan opsi format konversi secara logis
fun getTargetFormats(fileName: String, mimeType: String): List<String> {
    val originalExt = fileName.substringAfterLast('.', "").lowercase()
    val availableFormats = mutableListOf<String>()

    if (mimeType.startsWith("video/")) {
        availableFormats.addAll(listOf("mp4", "mkv", "avi", "gif", "mp3", "m4a"))
    } else if (mimeType.startsWith("image/")) {
        availableFormats.addAll(listOf("png", "jpg", "webp"))
    } else if (mimeType.startsWith("audio/")) {
        availableFormats.addAll(listOf("mp3", "m4a", "wav"))
    } else {
        // Fallback jika mime type tidak terbaca
        availableFormats.addAll(listOf("mp4", "png", "mp3"))
    }

    // Hapus format yang sama persis dengan file aslinya
    availableFormats.remove(originalExt)
    return availableFormats
}