package com.sipun.downly.data

import java.util.UUID

enum class MediaMode { VIDEO, AUDIO }
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETE, FAILED, CANCELLED }

data class DownloadRecord(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val detail: String = "Queued",
    val filePath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: String = "Best"
)

data class QualityOption(
    val quality: String,
    val height: Int,
    val estimatedBytes: Long?,
    val formatSelector: String = "",
    val sizeIsApproximate: Boolean = true
)
data class AudioOption(
    val formatId: String,
    val quality: String,
    val bitrate: Int,
    val estimatedBytes: Long?,
    val sizeIsApproximate: Boolean = true
)
data class MediaOptions(
    val qualityOptions: List<QualityOption>,
    val audioOptions: List<AudioOption>
)

data class MediaPreview(
    val title: String,
    val uploader: String,
    val duration: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Int = 0,
    val qualityOptions: List<QualityOption> = emptyList(),
    val audioOptions: List<AudioOption> = emptyList()
)

data class DuplicateDownloadPrompt(
    val recordId: String,
    val sourcePath: String,
    val existingUri: String,
    val existingPath: String,
    val mode: MediaMode,
    val fileName: String
)

data class ScreenState(
    val url: String = "",
    val initialized: Boolean = false,
    val busy: Boolean = false,
    val libraryProgress: Float? = null,
    val message: String = "Starting downloader…",
    val preview: MediaPreview? = null,
    val mode: MediaMode = MediaMode.VIDEO,
    val quality: String = "Smart",
    val connectionType: String = "Checking connection…",
    val recommendedQuality: String = "480p",
    val tab: Int = 0,
    val history: List<DownloadRecord> = emptyList(),
    val duplicatePrompt: DuplicateDownloadPrompt? = null,
    val themeMode: String = "System",
    val dynamicColor: Boolean = false
)
