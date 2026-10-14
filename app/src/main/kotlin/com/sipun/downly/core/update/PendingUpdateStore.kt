package com.sipun.downly.core.update

import android.content.Context
import java.io.File

internal class PendingUpdateStore {
    private companion object {
        const val PREFS = "app_updates"
        const val KEY_TAG = "pending_tag"
        const val KEY_VERSION = "pending_version"
        const val KEY_NAME = "pending_name"
        const val KEY_NOTES = "pending_notes"
        const val KEY_URL = "pending_url"
        const val KEY_FILE = "pending_file"
        const val KEY_SIZE = "pending_size"
        const val KEY_DIGEST = "pending_digest"
        const val KEY_RELEASE_DATE = "pending_release_date"
        const val KEY_NOTIFIED = "last_notified_tag"
    }

    fun save(context: Context, update: AppUpdate) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TAG, update.tag)
            .putString(KEY_VERSION, update.version)
            .putString(KEY_NAME, update.name)
            .putString(KEY_NOTES, update.notes)
            .putString(KEY_URL, update.downloadUrl)
            .putString(KEY_FILE, update.fileName)
            .putLong(KEY_SIZE, update.size)
            .putString(KEY_DIGEST, update.digest)
            .putString(KEY_RELEASE_DATE, update.releaseDate)
            .apply()
    }

    fun get(context: Context): AppUpdate? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tag = prefs.getString(KEY_TAG, null) ?: return null
        val version = prefs.getString(KEY_VERSION, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val notes = prefs.getString(KEY_NOTES, "").orEmpty()
        val url = prefs.getString(KEY_URL, null) ?: return null
        val file = prefs.getString(KEY_FILE, null) ?: return null
        val digest = prefs.getString(KEY_DIGEST, null)
            ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
            ?: return null
        return AppUpdate(
            tag = tag,
            version = version,
            name = name,
            notes = notes,
            downloadUrl = url,
            fileName = file,
            size = prefs.getLong(KEY_SIZE, 0),
            digest = digest,
            releaseDate = prefs.getString(KEY_RELEASE_DATE, null)
        )
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_FILE, null)?.let { fileName ->
            File(File(context.filesDir, "updates"), fileName).delete()
        }
        prefs.edit()
            .remove(KEY_TAG)
            .remove(KEY_VERSION)
            .remove(KEY_NAME)
            .remove(KEY_NOTES)
            .remove(KEY_URL)
            .remove(KEY_FILE)
            .remove(KEY_SIZE)
            .remove(KEY_DIGEST)
            .remove(KEY_RELEASE_DATE)
            .apply()
    }

    fun wasNotified(context: Context, tag: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NOTIFIED, null) == tag

    fun markNotified(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NOTIFIED, tag)
            .apply()
    }
}
