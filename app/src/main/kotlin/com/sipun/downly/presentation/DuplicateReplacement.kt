package com.sipun.downly.presentation

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
