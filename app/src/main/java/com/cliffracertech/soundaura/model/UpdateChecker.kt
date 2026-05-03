/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cliffracertech.soundaura.Dispatcher
import com.cliffracertech.soundaura.settings.PrefKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val downloadUrl: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val update: UpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Skipped : UpdateCheckResult
    data object Ignored : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

@Singleton
open class GitHubReleaseClient @Inject constructor() {
    open suspend fun latestReleaseJson(): String = withContext(Dispatcher.IO) {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection)
            .apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Mistysound")
            }
        try {
            if (connection.responseCode !in 200..299)
                error("GitHub latest release request failed")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/muhan81/Mistysound/releases/latest"
    }
}

@Singleton
class UpdateChecker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val releaseClient: GitHubReleaseClient,
) {
    private val lastAutoUpdateCheckDayKey =
        stringPreferencesKey(PrefKeys.lastAutoUpdateCheckDay)
    private val ignoredUpdateTagKey =
        stringPreferencesKey(PrefKeys.ignoredUpdateTag)

    internal var currentDayProvider: () -> String = { LocalDate.now().toString() }

    suspend fun checkLatest(currentVersionName: String): UpdateCheckResult =
        try {
            releaseResultFromJson(releaseClient.latestReleaseJson(), currentVersionName)
        } catch (exception: Exception) {
            if (exception is CancellationException)
                throw exception
            UpdateCheckResult.Failed
        }

    suspend fun checkAutomatically(currentVersionName: String): UpdateCheckResult {
        val today = currentDayProvider()
        val prefs = dataStore.data.first()
        if (prefs[lastAutoUpdateCheckDayKey] == today)
            return UpdateCheckResult.Skipped

        val result = checkLatest(currentVersionName)
        if (result == UpdateCheckResult.Failed)
            return result

        dataStore.edit { it[lastAutoUpdateCheckDayKey] = today }
        return when {
            result is UpdateCheckResult.UpdateAvailable &&
                prefs[ignoredUpdateTagKey] == result.update.tagName ->
                    UpdateCheckResult.Ignored
            else -> result
        }
    }

    suspend fun ignoreUpdate(tagName: String) {
        dataStore.edit { it[ignoredUpdateTagKey] = tagName }
    }

    companion object {
        internal fun releaseResultFromJson(
            releaseJson: String,
            currentVersionName: String,
        ): UpdateCheckResult {
            val release = JSONObject(releaseJson)
            if (release.optBoolean("draft") || release.optBoolean("prerelease"))
                return UpdateCheckResult.UpToDate

            val tagName = release.optString("tag_name").takeIf(String::isNotBlank)
                ?: return UpdateCheckResult.Failed
            val versionName = tagName.trim().removePrefix("v").removePrefix("V")
            if (!isNewerVersion(versionName, currentVersionName))
                return UpdateCheckResult.UpToDate

            val expectedApkName = "Mistysound-$versionName-public-release.apk"
            val assets = release.optJSONArray("assets") ?: return UpdateCheckResult.Failed
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (!asset.optString("name").equals(expectedApkName, ignoreCase = true))
                    continue
                val downloadUrl = asset.optString("browser_download_url")
                    .takeIf(String::isNotBlank) ?: return UpdateCheckResult.Failed
                return UpdateCheckResult.UpdateAvailable(
                    UpdateInfo(tagName, versionName, downloadUrl))
            }
            return UpdateCheckResult.Failed
        }

        internal fun isNewerVersion(candidate: String, current: String): Boolean =
            compareVersions(candidate, current)?.let { it > 0 } == true

        internal fun compareVersions(left: String, right: String): Int? {
            val leftParts = parseVersion(left) ?: return null
            val rightParts = parseVersion(right) ?: return null
            val maxSize = maxOf(leftParts.size, rightParts.size)
            for (index in 0 until maxSize) {
                val leftPart = leftParts.getOrElse(index) { 0 }
                val rightPart = rightParts.getOrElse(index) { 0 }
                if (leftPart != rightPart)
                    return leftPart.compareTo(rightPart)
            }
            return 0
        }

        private fun parseVersion(version: String): List<Int>? {
            val normalized = version.trim().removePrefix("v").removePrefix("V")
            if (!Regex("""\d+(\.\d+)*""").matches(normalized))
                return null
            return normalized.split('.').map { it.toIntOrNull() ?: return null }
        }
    }
}
