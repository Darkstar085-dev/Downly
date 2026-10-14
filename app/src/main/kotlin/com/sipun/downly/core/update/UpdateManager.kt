package com.sipun.downly.core.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val percent: Int,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false
)

data class AppUpdate(
    val tag: String,
    val version: String,
    val name: String,
    val notes: String,
    val downloadUrl: String,
    val fileName: String,
    val size: Long,
    val digest: String,
    val releaseDate: String?
)

object UpdateManager {
    const val ACTION_DOWNLOAD_UPDATE = "com.sipun.downly.DOWNLOAD_UPDATE"
    const val ACTION_SHOW_UPDATE = "com.sipun.downly.SHOW_UPDATE"
    const val ACTION_INSTALL_UPDATE = "com.sipun.downly.INSTALL_UPDATE"

    internal const val REPOSITORY = "Darkstar085/Downly"
    internal const val API_BASE = "https://api.github.com/repos/" + REPOSITORY
    internal const val LATEST_API_URL = API_BASE + "/releases/latest"
    internal const val MANIFEST_URL =
        "https://github.com/" + REPOSITORY + "/releases/latest/download/app-release.json"

    private val releaseClient = UpdateReleaseClient()
    private val pendingStore = PendingUpdateStore()

    suspend fun findLatestUpdate(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val currentVersion = currentVersion(context)
        releaseClient.loadManifest(currentVersion)
            ?: releaseClient.loadLatestRelease(currentVersion)
    }

    suspend fun resolveDownloadUrl(browserDownloadUrl: String): String =
        withContext(Dispatchers.IO) {
            releaseClient.loadReleaseByTag(browserDownloadUrl)
        }

    fun savePendingUpdate(context: Context, update: AppUpdate) {
        pendingStore.save(context, update)
    }

    fun getPendingUpdate(context: Context): AppUpdate? {
        val pending = pendingStore.get(context) ?: return null
        if (!UpdateVersioning.isNewerVersion(currentVersion(context), pending.version)) {
            clearPendingUpdate(context)
            return null
        }
        return pending
    }

    suspend fun getValidatedPendingUpdate(context: Context): AppUpdate? =
        withContext(Dispatchers.IO) {
            val pending = getPendingUpdate(context) ?: return@withContext null
            try {
                val validated = releaseClient.validatePending(currentVersion(context), pending)
                if (validated == null) {
                    clearPendingUpdate(context)
                    null
                } else {
                    savePendingUpdate(context, validated)
                    validated
                }
            } catch (_: Exception) {
                pending
            }
        }

    fun getDownloadedUpdate(context: Context): AppUpdate? {
        val update = getPendingUpdate(context) ?: return null
        val apk = File(File(context.filesDir, "updates"), update.fileName)
        return update.takeIf {
            apk.isFile &&
                    apk.length() > 0L &&
                    UpdateVerifier.verifyDigest(apk, update.digest)
        }
    }

    fun clearPendingUpdate(context: Context) {
        pendingStore.clear(context)
    }

    fun wasNotified(context: Context, tag: String): Boolean =
        pendingStore.wasNotified(context, tag)

    fun markNotified(context: Context, tag: String) {
        pendingStore.markNotified(context, tag)
    }

    fun enqueuePeriodicCheck(context: Context) {
        UpdateWorkScheduler.enqueuePeriodicCheck(context)
    }

    fun enqueueDownload(context: Context, update: AppUpdate? = null) {
        val target = update ?: getPendingUpdate(context) ?: return
        UpdateWorkScheduler.enqueueDownload(context, target)
    }

    fun getDownloadProgress(context: Context, tag: String): DownloadProgress? =
        UpdateWorkScheduler.getDownloadProgress(context, tag)

    fun observeDownloadProgress(context: Context, tag: String): Flow<DownloadProgress?> =
        UpdateWorkScheduler.observeDownloadProgress(context, tag)

    fun cancelDownload(context: Context, tag: String) {
        UpdateWorkScheduler.cancelDownload(context, tag)
    }

    private fun currentVersion(context: Context): String =
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            .orEmpty()
}
