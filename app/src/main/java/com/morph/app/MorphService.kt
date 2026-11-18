package com.morph.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.morph.app.core.AppStrings
import com.morph.app.core.ConversionJob
import com.morph.app.core.ConversionQueueManager
import com.morph.app.core.JobStatus
import kotlinx.coroutines.*

class MorphService : Service() {

    private val CHANNEL_ID = "Morph_Foreground"
    private val NOTIFICATION_ID = 143
    private val SUMMARY_NOTIFICATION_ID = 144

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueExecutionJob: Job? = null
    @Volatile private var isCancelled = false

    companion object {
        const val ACTION_START_QUEUE = "com.morph.app.ACTION_START_QUEUE"
        const val ACTION_CANCEL_CONVERSION = "ACTION_CANCEL_CONVERSION"
        const val ACTION_CANCEL_SINGLE_JOB = "com.morph.app.ACTION_CANCEL_SINGLE_JOB"
        const val EXTRA_JOB_ID = "EXTRA_JOB_ID"

        /** The type every tested device accepts; used if the system rejects the preferred one. */
        @SuppressLint("InlinedApi") // compile-time int, only passed on Android 10+
        internal const val FALLBACK_FOREGROUND_SERVICE_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

        /**
         * mediaProcessing on Android 15+ (the type meant for transcoding), dataSync on 10-14,
         * none before 10. Never 0 from Android 10: targetSdk 34+ throws on "type none".
         */
        @SuppressLint("InlinedApi") // compile-time ints; [sdk] picks one the device knows
        internal fun foregroundServiceType(sdk: Int): Int = when {
            sdk >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            sdk >= 29 -> FALLBACK_FOREGROUND_SERVICE_TYPE
            else -> 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Hubungkan pembatalan dari manager ke MorphEngine
        ConversionQueueManager.onCancelActiveConversion = {
            MorphEngine.cancelCurrentConversion()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppStrings.setLanguage(com.morph.app.MorphPrefs(this).appLanguage)

        when (intent?.action) {
            ACTION_CANCEL_CONVERSION -> {
                isCancelled = true
                ConversionQueueManager.cancelAllJobs()
                stopForegroundAndService()
                return START_NOT_STICKY
            }

            ACTION_CANCEL_SINGLE_JOB -> {
                val jobId = intent.getStringExtra(EXTRA_JOB_ID)
                if (jobId != null) {
                    ConversionQueueManager.cancelJob(jobId)
                }
                return START_NOT_STICKY
            }
        }

        isCancelled = false

        // Dukungan kompatibilitas backward jika intent membawa file tunggal
        val inputUri = intent?.let { IntentCompat.getParcelableExtra(it, "INPUT_URI", Uri::class.java) }
        val targetFormat = intent?.getStringExtra("TARGET_FORMAT") ?: ""
        val qualityPreset = intent?.getIntExtra("QUALITY_PRESET", 0) ?: 0

        if (inputUri != null && targetFormat.isNotEmpty()) {
            val originalName = getFileNameFromUri(this, inputUri)
            val mimeType = contentResolver.getType(inputUri) ?: "*/*"
            ConversionQueueManager.addJob(
                inputUri = inputUri,
                originalFileName = originalName,
                mimeType = mimeType,
                targetFormat = targetFormat,
                qualityPreset = qualityPreset
            )
        }

        // Mulai eksekusi antrean
        if (ConversionQueueManager.hasActiveOrWaitingJobs()) {
            val strings = AppStrings.get()
            val initialNotif = buildNotification(
                title = strings.serviceRunningTitle,
                content = strings.servicePreparingQueue,
                isProcessing = true,
                progress = 0
            )
            enterForeground(initialNotif)
            processQueue()
        } else {
            stopForegroundAndService()
        }

        return START_NOT_STICKY
    }

    /**
     * Platform call, not ServiceCompat: androidx.core 1.18 ANDs the type with 0x40000FFF, which
     * turns mediaProcessing (0x2000) into 0 = "type none" and crashes on targetSdk 34+.
     */
    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification) // no service types before Android 10
            return
        }
        val type = foregroundServiceType(Build.VERSION.SDK_INT)
        try {
            startForeground(NOTIFICATION_ID, notification, type)
        } catch (e: RuntimeException) {
            // Type-specific rejections are fixed by dataSync; anything else fails the retry the same way
            if (type == FALLBACK_FOREGROUND_SERVICE_TYPE) throw e
            Log.w("MorphService", "Foreground type $type rejected, retrying with dataSync", e)
            startForeground(NOTIFICATION_ID, notification, FALLBACK_FOREGROUND_SERVICE_TYPE)
        }
    }

    private fun processQueue() {
        if (queueExecutionJob?.isActive == true) {
            return // Antrean sudah berjalan, sequential processing tetap terjaga
        }

        queueExecutionJob = serviceScope.launch {
            while (isActive && !isCancelled) {
                val job = ConversionQueueManager.getNextWaitingJob() ?: break

                ConversionQueueManager.updateJobStatus(job.id, JobStatus.PROCESSING)
                updateNotificationForJob(job, 0, AppStrings.get().serviceStartingConversion)

                try {
                    var resultDetail: String? = null
                    val allUris = listOf(job.inputUri) + job.extraInputUris
                    val finalPath = if (allUris.size > 1 && job.targetFormat.equals("pdf", ignoreCase = true)) {
                        MorphEngine.mergeImagesToPdf(
                            context = this@MorphService,
                            inputUris = allUris,
                            originalFileName = job.originalFileName,
                            qualityPreset = job.qualityPreset,
                            onProgress = { stepMsg, percent ->
                                ConversionQueueManager.updateJobProgress(job.id, percent, stepMsg)
                                if (!MainActivity.isAppInForeground && !isCancelled) {
                                    updateNotificationForJob(job, percent, stepMsg)
                                }
                            }
                        )
                    } else {
                        MorphEngine.executeConversion(
                            context = this@MorphService,
                            inputUri = job.inputUri,
                            outputExtension = job.targetFormat,
                            originalFileName = job.originalFileName,
                            qualityPreset = job.qualityPreset,
                            onProgress = { stepMsg, percent ->
                                ConversionQueueManager.updateJobProgress(job.id, percent, stepMsg)
                                // Smart Notification: hanya update notifikasi di background saat aplikasi aktif
                                if (!MainActivity.isAppInForeground && !isCancelled) {
                                    updateNotificationForJob(job, percent, stepMsg)
                                }
                            },
                            videoResolution = job.videoResolution,
                            videoFps = job.videoFps,
                            onResultDetail = { resultDetail = it }
                        )
                    }

                    ConversionQueueManager.updateJobStatus(job.id, JobStatus.COMPLETED, filePath = finalPath, detail = resultDetail)
                } catch (e: CancellationException) {
                    // Keep a FAILED set by onTimeout instead of overwriting it with "cancelled"
                    val current = ConversionQueueManager.queueState.value.find { it.id == job.id }
                    if (current?.status == JobStatus.PROCESSING) {
                        ConversionQueueManager.updateJobStatus(job.id, JobStatus.CANCELLED)
                    }
                    if (isCancelled || !ConversionQueueManager.hasActiveOrWaitingJobs()) break
                } catch (e: OutOfMemoryError) {
                    // An Error, not an Exception: uncaught it would kill the app and the whole batch
                    Log.e("MorphService", "Out of memory on job ${job.id}: ${job.originalFileName}", e)
                    ConversionQueueManager.updateJobStatus(job.id, JobStatus.FAILED, error = AppStrings.get().engineErrOutOfMemory)
                } catch (e: Exception) {
                    Log.e("MorphService", "Gagal memproses job ${job.id}: ${job.originalFileName}", e)
                    ConversionQueueManager.updateJobStatus(
                        job.id,
                        JobStatus.FAILED,
                        error = e.message ?: AppStrings.get().queueFailed
                    )
                    // Sequential processing: Kegagalan 1 item TIDAK menghentikan item antrean berikutnya
                }
            }

            // Seluruh antrean selesai diproses
            handleQueueCompleted()
        }
    }

    private fun updateNotificationForJob(job: ConversionJob, progress: Int, message: String) {
        val summary = ConversionQueueManager.getQueueSummary()
        val total = summary.totalCount
        val currentIdx = summary.currentJobIndex

        val strings = AppStrings.get()
        val title = if (total > 1) {
            strings.notifConvertingBatchTitle(currentIdx, total, job.originalFileName)
        } else {
            strings.notifConvertingSingleTitle(job.originalFileName, job.targetFormat)
        }

        val content = "$message ($progress%)"
        val notification = buildNotification(title, content, true, progress)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun handleQueueCompleted() {
        stopForeground(STOP_FOREGROUND_REMOVE)

        val summary = ConversionQueueManager.getQueueSummary()
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)

        // Tampilkan notifikasi ringkasan akhir HANYA jika berada di background dan tidak dibatalkan total
        if (!MainActivity.isAppInForeground && !isCancelled && summary.totalCount > 0) {
            val strings = AppStrings.get()
            val title = if (summary.failedCount == 0) {
                strings.notifSuccessTitle
            } else {
                strings.notifPartialTitle
            }

            val singleDetail = ConversionQueueManager.queueState.value
                .singleOrNull { it.status == JobStatus.COMPLETED }?.resultDetail
            val body = if (summary.failedCount == 0) {
                if (summary.completedCount == 1) {
                    if (singleDetail != null) "${strings.notifSuccessBodySingle} $singleDetail" else strings.notifSuccessBodySingle
                } else strings.notifSuccessBodyBatch(summary.completedCount)
            } else {
                strings.notifSummaryBody(summary.completedCount, summary.failedCount)
            }

            val finishNotif = buildNotification(title, body, false)
            manager.notify(SUMMARY_NOTIFICATION_ID, finishNotif)
        }

        stopSelf()
    }

    private fun stopForegroundAndService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val manager = getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        manager.cancel(SUMMARY_NOTIFICATION_ID)
        stopSelf()
    }

    private fun buildNotification(
        title: String,
        content: String,
        isProcessing: Boolean,
        progress: Int = 0
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.morph_logo_monochrome)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isProcessing) {
            builder.setProgress(100, progress.coerceIn(0, 100), false)
            builder.setOngoing(true)

            val cancelIntent = Intent(this, MorphService::class.java).apply {
                action = ACTION_CANCEL_CONVERSION
            }
            val cancelPendingIntent = PendingIntent.getService(
                this, 1, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, AppStrings.get().notifActionCancelAll, cancelPendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Morph Conversions", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // Android 15+: dataSync and mediaProcessing services each get ~6h per 24h. The system calls
    // onTimeout and kills the app if the service is still running a few seconds later, so stop cleanly.
    override fun onTimeout(startId: Int) = stopOnTimeout()

    override fun onTimeout(startId: Int, fgsType: Int) = stopOnTimeout()

    private fun stopOnTimeout() {
        Log.w("MorphService", "Foreground service time limit reached, cancelling queue")
        isCancelled = true
        // The user didn't cancel: show why and that Retry works later, instead of "Cancelled"
        ConversionQueueManager.failUnfinishedJobs(AppStrings.get().engineErrServiceTimeLimit)
        stopForegroundAndService()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
