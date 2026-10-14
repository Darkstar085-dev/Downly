package com.sipun.downly.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String = "",
    val body: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
data class GithubAsset(
    @SerialName("url") val apiUrl: String,
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("content_type") val contentType: String = "",
    val size: Long = 0,
    val digest: String? = null
)

@Serializable
private data class UpdateManifest(
    val version: String,
    val tag: String,
    val name: String = "",
    val description: String = "",
    val download_url: String,
    val size: Long = 0,
    val sha256: String,
    val published_at: String? = null
)

internal class UpdateReleaseClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun loadManifest(currentVersion: String): AppUpdate? {
        val connection = openMetadataConnection(UpdateManager.MANIFEST_URL)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return null
                throw IOException(
                    "GitHub manifest request failed: HTTP " + connection.responseCode
                )
            }
            val manifest = connection.inputStream.bufferedReader().use {
                json.decodeFromString<UpdateManifest>(it.readText())
            }
            if (!UpdateVersioning.isNewerVersion(currentVersion, manifest.version)) return null
            AppUpdate(
                tag = manifest.tag,
                version = manifest.version,
                name = manifest.name.ifBlank { "Downly " + manifest.version },
                notes = manifest.description.toReleaseNotes(),
                downloadUrl = manifest.download_url,
                fileName = manifest.download_url.substringAfterLast('/'),
                size = manifest.size,
                digest = "sha256:" + manifest.sha256.removePrefix("sha256:"),
                releaseDate = manifest.published_at?.let(UpdateVersioning::formatReleaseDate)
            )
        } finally {
            connection.disconnect()
        }
    }

    fun loadLatestRelease(currentVersion: String): AppUpdate? =
        loadRelease(currentVersion, UpdateManager.LATEST_API_URL)

    fun loadReleaseByTag(browserDownloadUrl: String): String {
        val direct = URL(browserDownloadUrl)
        val path = direct.path.trim('/').split('/')
        if (path.size < 5 || path[2] != "releases" || path[3] != "download") {
            return browserDownloadUrl
        }
        val owner = path[0]
        val repository = path[1]
        val tag = URLDecoder.decode(path[4], StandardCharsets.UTF_8.name())
        val fileName = URLDecoder.decode(path.last(), StandardCharsets.UTF_8.name())
        val endpoint =
            "https://api.github.com/repos/" + owner + "/" + repository +
                    "/releases/tags/" + encodePath(tag)
        val connection = openMetadataConnection(endpoint)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return browserDownloadUrl
            val release = connection.inputStream.bufferedReader().use {
                json.decodeFromString<GithubRelease>(it.readText())
            }
            release.assets.firstOrNull { it.name == fileName }?.apiUrl ?: browserDownloadUrl
        } finally {
            connection.disconnect()
        }
    }

    fun validatePending(currentVersion: String, fallback: AppUpdate): AppUpdate? {
        loadManifest(currentVersion)?.let { return it }
        val connection = openMetadataConnection(UpdateManager.LATEST_API_URL)
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val release = connection.inputStream.bufferedReader().use {
                        json.decodeFromString<GithubRelease>(it.readText())
                    }
                    release.toAppUpdate(currentVersion)
                }

                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> fallback
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun loadRelease(currentVersion: String, endpoint: String): AppUpdate? {
        val connection = openMetadataConnection(endpoint)
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(
                    "GitHub API request failed: HTTP " + connection.responseCode
                )
            }
            val release = connection.inputStream.bufferedReader().use {
                json.decodeFromString<GithubRelease>(it.readText())
            }
            release.toAppUpdate(currentVersion)
        } finally {
            connection.disconnect()
        }
    }

    private fun GithubRelease.toAppUpdate(currentVersion: String): AppUpdate? {
        if (draft) return null
        val version = tagName.removePrefix("v").trim()
        if (!UpdateVersioning.isNewerVersion(currentVersion, version)) return null
        val asset = assets.firstOrNull {
            it.name.endsWith(".apk", ignoreCase = true) &&
                    it.contentType.equals(
                        "application/vnd.android.package-archive",
                        ignoreCase = true
                    ) &&
                    it.digest?.startsWith("sha256:", ignoreCase = true) == true
        } ?: return null
        return AppUpdate(
            tag = tagName,
            version = version,
            name = name.ifBlank { "Downly " + version },
            notes = body.toReleaseNotes(),
            downloadUrl = asset.downloadUrl,
            fileName = asset.name,
            size = asset.size,
            digest = asset.digest.orEmpty(),
            releaseDate = publishedAt?.let(UpdateVersioning::formatReleaseDate)
        )
    }

    private fun openMetadataConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Downly")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        }

    private fun encodePath(value: String): String =
        value.split('/').joinToString("/") {
            java.net.URLEncoder.encode(it, StandardCharsets.UTF_8.name())
                .replace("+", "%20")
        }
}
