package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord

internal fun enqueueDownloadRecord(
    history: List<DownloadRecord>,
    record: DownloadRecord
): List<DownloadRecord> = listOf(record) + history

internal fun mergeWorkerRecordIntoHistory(
    history: List<DownloadRecord>,
    workerRecord: DownloadRecord?
): List<DownloadRecord> {
    val updated = workerRecord ?: return history
    return history.map { record -> if (record.id == updated.id) updated else record }
}
