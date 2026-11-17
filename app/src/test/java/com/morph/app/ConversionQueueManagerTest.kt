package com.morph.app

import android.net.TestUri
import com.morph.app.core.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConversionQueueManagerTest {

    @Before
    fun setUp() {
        ConversionQueueManager.clearAll()
    }

    @Test
    fun testAddSingleJobAndRetrieveNext() {
        val dummyUri = TestUri("content://dummy/sample.png")
        val jobId = ConversionQueueManager.addJob(
            inputUri = dummyUri,
            originalFileName = "sample.png",
            mimeType = "image/png",
            targetFormat = "pdf",
            qualityPreset = 0
        )

        assertNotNull(jobId)
        assertTrue(ConversionQueueManager.hasActiveOrWaitingJobs())

        val nextJob = ConversionQueueManager.getNextWaitingJob()
        assertNotNull(nextJob)
        assertEquals(jobId, nextJob?.id)
        assertEquals(JobStatus.WAITING, nextJob?.status)
        assertEquals("sample.png", nextJob?.originalFileName)
        assertEquals("pdf", nextJob?.targetFormat)
    }

    @Test
    fun testAddBatchJobsAndSequentialProcessing() {
        val dummyUri = TestUri("content://dummy/batch")
        val requests = listOf(
            ConversionJobRequest(dummyUri, "file1.png", "image/png", "pdf", 0),
            ConversionJobRequest(dummyUri, "file2.png", "image/png", "pdf", 0),
            ConversionJobRequest(dummyUri, "file3.flac", "audio/flac", "mp3", 1)
        )

        val jobIds = ConversionQueueManager.addJobs(requests)
        assertEquals(3, jobIds.size)

        var summary = ConversionQueueManager.getQueueSummary()
        assertEquals(3, summary.totalCount)
        assertEquals(3, summary.waitingCount)
        assertEquals(0, summary.completedCount)

        // Process first job
        val job1 = ConversionQueueManager.getNextWaitingJob()
        assertNotNull(job1)
        assertEquals(jobIds[0], job1?.id)

        ConversionQueueManager.updateJobStatus(job1!!.id, JobStatus.PROCESSING)
        ConversionQueueManager.updateJobProgress(job1.id, 50, "Memproses...")
        val activeJob = ConversionQueueManager.getActiveJob()
        assertEquals(job1.id, activeJob?.id)
        assertEquals(50, activeJob?.progress)

        // Complete job 1
        ConversionQueueManager.updateJobStatus(job1.id, JobStatus.COMPLETED, filePath = "/storage/file1.pdf")

        // Next job should be job 2
        val job2 = ConversionQueueManager.getNextWaitingJob()
        assertNotNull(job2)
        assertEquals(jobIds[1], job2?.id)

        // Fail job 2
        ConversionQueueManager.updateJobStatus(job2!!.id, JobStatus.PROCESSING)
        ConversionQueueManager.updateJobStatus(job2.id, JobStatus.FAILED, error = "Corrupt file")

        // Next job should be job 3 (failure of job 2 does not stop queue)
        val job3 = ConversionQueueManager.getNextWaitingJob()
        assertNotNull(job3)
        assertEquals(jobIds[2], job3?.id)

        summary = ConversionQueueManager.getQueueSummary()
        assertEquals(3, summary.totalCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(1, summary.waitingCount)
    }

    @Test
    fun testCancellation() {
        val dummyUri = TestUri("content://dummy/video.mp4")
        var cancelledCallbackTriggered = false
        ConversionQueueManager.onCancelActiveConversion = {
            cancelledCallbackTriggered = true
        }

        val jobId = ConversionQueueManager.addJob(dummyUri, "video.mp4", "video/mp4", "mkv", 0)
        ConversionQueueManager.updateJobStatus(jobId, JobStatus.PROCESSING)

        ConversionQueueManager.cancelJob(jobId)
        assertTrue(cancelledCallbackTriggered)

        val job = ConversionQueueManager.queueState.value.first()
        assertEquals(JobStatus.CANCELLED, job.status)
        assertFalse(ConversionQueueManager.hasActiveOrWaitingJobs())
    }

    @Test
    fun testStaleQueueClearedWhenNewBatchAdded() {
        val dummyUri = TestUri("content://dummy/video.mp4")

        // Skenario: Batch 1 selesai atau dibatalkan
        val oldJobId = ConversionQueueManager.addJob(dummyUri, "old.mp4", "video/mp4", "mkv", 0)
        ConversionQueueManager.updateJobStatus(oldJobId, JobStatus.PROCESSING)
        ConversionQueueManager.cancelJob(oldJobId)

        // Verifikasi tidak ada job aktif atau waiting
        assertFalse(ConversionQueueManager.hasActiveOrWaitingJobs())

        // Skenario Bug 1: Pengguna mengirim 2 file baru di sesi berikutnya
        val newRequests = listOf(
            ConversionJobRequest(dummyUri, "new1.png", "image/png", "pdf", 0),
            ConversionJobRequest(dummyUri, "new2.png", "image/png", "pdf", 0)
        )
        val newJobIds = ConversionQueueManager.addJobs(newRequests)

        // Verifikasi antrean lama otomatis dibersihkan dan total sekarang tepat 2 (bukan 3 atau 2x lipat)
        val summary = ConversionQueueManager.getQueueSummary()
        assertEquals(2, summary.totalCount)
        assertEquals(2, newJobIds.size)
        assertEquals(2, ConversionQueueManager.queueState.value.size)
        assertEquals("new1.png", ConversionQueueManager.queueState.value[0].originalFileName)
        assertEquals("new2.png", ConversionQueueManager.queueState.value[1].originalFileName)
    }
}
