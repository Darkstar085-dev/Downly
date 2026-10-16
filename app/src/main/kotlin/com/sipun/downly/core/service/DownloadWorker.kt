package com.sipun.downly.core.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sipun.downly.MainActivity
import com.sipun.downly.R
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.MediaMode
import com.sipun.downly.presentation.DownlyViewModel
import com.sipun.downly.core.notification.DownloadNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet

private const val ID_KEY = "download_id"
private const val URL_KEY = "download_url"
private const val TITLE_KEY = "download_title"
private const val MODE_KEY = "download_mode"
private const val QUALITY_KEY = "download_quality"
private const val CONNECTION_KEY = "download_connection"

object DownloadWorkManager {
    private val completionListeners = CopyOnWriteArraySet<(String) -> Unit>()
    private val progressListeners = CopyOnWriteArraySet<(Int, Int, String) -> Unit>()

    fun enqueue(context: Context, record: DownloadRecord, connectionType: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    ID_KEY to record.id,
                    URL_KEY to record.url,
                    TITLE_KEY to record.title,
                    MODE_KEY to record.mode.name,
                    QUALITY_KEY to record.quality,
                    CONNECTION_KEY to connectionType
                )
            )
            .addTag("downly-download")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "downly-download-${record.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("downly-download-$id")
    }

    fun addCompletionListener(listener: (String) -> Unit): () -> Unit {
        completionListeners += listener
        return { completionListeners -= listener }
    }

    fun notifyFinished(id: String) {
        completionListeners.forEach { listener -> listener(id) }
    }

    fun addProgressListener(listener: (Int, Int, String) -> Unit): () -> Unit {
        progressListeners += listener
        return { progressListeners -= listener }
    }

    fun notifyProgress(notificationId: Int, percent: Int, detail: String) {
        progressListeners.forEach { listener -> listener(notificationId, percent, detail) }
    }
}

class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(ID_KEY).orEmpty()
        return ForegroundInfo(id.hashCode(), notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(ID_KEY) ?: return@withContext Result.failure()
        val mode = inputData.getString(MODE_KEY)?.let { runCatching { MediaMode.valueOf(it) }.getOrNull() }
            ?: return@withContext Result.failure()
        val record = DownloadRecord(
            id = id,
            url = inputData.getString(URL_KEY).orEmpty(),
            title = inputData.getString(TITLE_KEY).orEmpty(),
            status = com.sipun.downly.data.DownloadStatus.QUEUED,
            mode = mode,
            quality = inputData.getString(QUALITY_KEY).orEmpty()
        )
        setForeground(getForegroundInfo())

        val viewModel = DownlyViewModel()
        viewModel.attach(applicationContext, observeDownloadCompletion = false)
        var initialized = false
        repeat(120) {
            if (viewModel.state.value.initialized) {
                initialized = true
                return@repeat
            }
            delay(500)
        }
        if (!initialized) return@withContext Result.retry()

        viewModel.executeDownloadFromWorker(
            record,
            mode,
            record.quality,
            inputData.getString(CONNECTION_KEY).orEmpty()
        )
        viewModel.flushHistoryForWorker()
        DownloadWorkManager.notifyFinished(id)
        Result.success()
    }

    private fun notification(): Notification {
        DownloadNotificationHelper.createChannel(applicationContext)
        val id = inputData.getString(ID_KEY).orEmpty()
        val title = inputData.getString(TITLE_KEY).orEmpty().ifBlank { "Downloading media…" }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(applicationContext, "media_downloads")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Starting download…")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, 0, true)
            .build()
    }
}
