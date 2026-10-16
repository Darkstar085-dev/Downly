package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord

internal fun replaceWithRollback(
    backup: () -> Unit,
    replace: () -> Unit,
    restore: () -> Unit,
) {
    backup()
    try {
        replace()
    } catch (error: Exception) {
        try {
            restore()
        } catch (restoreError: Exception) {
            error.addSuppressed(restoreError)
        }
        throw error
    }
}

internal fun reconcileReplacedHistory(
    history: List<DownloadRecord>,
    replacement: DownloadRecord,
    replacedPath: String
): List<DownloadRecord> = history.filterNot {
    it.id != replacement.id && it.filePath == replacedPath
}
