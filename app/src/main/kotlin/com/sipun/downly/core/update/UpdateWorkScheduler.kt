package com.sipun.downly.core.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

internal object UpdateWorkScheduler {
    private const val CHECK_WORK = "release_update_check"

    fun enqueuePeriodicCheck(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CHECK_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueDownload(context: Context, update: AppUpdate) {
        val data = Data.Builder()
            .putString(UpdateDownloadWorker.KEY_TAG, update.tag)
            .putString(UpdateDownloadWorker.KEY_URL, update.downloadUrl)
            .putString(UpdateDownloadWorker.KEY_FILE, update.fileName)
            .putString(UpdateDownloadWorker.KEY_DIGEST, update.digest)
            .putLong(UpdateDownloadWorker.KEY_SIZE, update.size)
            .build()
        val request = OneTimeWorkRequestBuilder<UpdateDownloadWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "update_download_" + update.tag,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun getDownloadProgress(context: Context, tag: String): DownloadProgress? =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("update_download_" + tag)
            .get()
            .firstOrNull()
            ?.toDownloadProgress()

    fun observeDownloadProgress(context: Context, tag: String): Flow<DownloadProgress?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("update_download_" + tag)
            .map { infos -> infos.firstOrNull()?.toDownloadProgress() }
            .distinctUntilChanged()

    fun cancelDownload(context: Context, tag: String) {
        WorkManager.getInstance(context).cancelUniqueWork("update_download_" + tag)
    }

    private fun WorkInfo.toDownloadProgress(): DownloadProgress? {
        val downloaded = progress.getLong(UpdateDownloadWorker.PROGRESS_DOWNLOADED, 0L)
        val total = progress.getLong(UpdateDownloadWorker.PROGRESS_TOTAL, 0L)
        val percent = progress.getInt(UpdateDownloadWorker.PROGRESS_PERCENT, 0)
        return when (state) {
            WorkInfo.State.SUCCEEDED -> DownloadProgress(downloaded, total, 100, isFinished = true)
            WorkInfo.State.FAILED -> DownloadProgress(downloaded, total, percent, isFailed = true)
            WorkInfo.State.CANCELLED -> null
            else -> DownloadProgress(downloaded, total, percent)
        }
    }
}
