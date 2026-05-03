/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.model.GitHubReleaseClient
import com.cliffracertech.soundaura.model.UpdateCheckResult
import com.cliffracertech.soundaura.model.UpdateChecker
import com.cliffracertech.soundaura.settings.PrefKeys
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateCheckerTests {
    @get:Rule val testScopeRule = TestScopeRule()
    @get:Rule val dataStoreTestRule = DataStoreTestRule(testScopeRule.scope)

    private val dataStore get() = dataStoreTestRule.dataStore
    private val lastAutoUpdateCheckDayKey =
        stringPreferencesKey(PrefKeys.lastAutoUpdateCheckDay)

    @Test fun semantic_version_comparison() {
        assertThat(UpdateChecker.isNewerVersion("v3.2.6", "3.2.5")).isTrue()
        assertThat(UpdateChecker.isNewerVersion("v3.2.5", "3.2.5")).isFalse()
        assertThat(UpdateChecker.isNewerVersion("v3.2.10", "v3.2.9")).isTrue()
        assertThat(UpdateChecker.isNewerVersion("not-a-version", "3.2.5")).isFalse()
    }

    @Test fun release_json_selects_public_release_apk() {
        val result = UpdateChecker.releaseResultFromJson(
            releaseJson(
                tagName = "v3.2.6",
                "Mistysound-3.2.6-personal-release.apk" to
                    "https://example.com/personal.apk",
                "Mistysound-3.2.6-public-release.apk" to
                    "https://example.com/public.apk",
            ),
            currentVersionName = "3.2.5",
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = (result as UpdateCheckResult.UpdateAvailable).update
        assertThat(update.tagName).isEqualTo("v3.2.6")
        assertThat(update.versionName).isEqualTo("3.2.6")
        assertThat(update.downloadUrl).isEqualTo("https://example.com/public.apk")
    }

    @Test fun automatic_check_records_successful_day_and_skips_repeated_checks() =
        runTest {
            val fakeClient = FakeGitHubReleaseClient(
                releaseJson(
                    tagName = "v3.2.6",
                    "Mistysound-3.2.6-public-release.apk" to
                        "https://example.com/public.apk",
                )
            )
            val checker = UpdateChecker(dataStore, fakeClient)
            var currentDay = "2026-05-03"
            checker.currentDayProvider = { currentDay }

            val firstResult = checker.checkAutomatically("3.2.5")
            assertThat(firstResult)
                .isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
            assertThat(fakeClient.callCount).isEqualTo(1)
            assertThat(dataStore.data.first()[lastAutoUpdateCheckDayKey])
                .isEqualTo("2026-05-03")

            assertThat(checker.checkAutomatically("3.2.5"))
                .isEqualTo(UpdateCheckResult.Skipped)
            assertThat(fakeClient.callCount).isEqualTo(1)

            currentDay = "2026-05-04"
            assertThat(checker.checkAutomatically("3.2.5"))
                .isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
            assertThat(fakeClient.callCount).isEqualTo(2)
        }

    @Test fun automatic_check_skips_ignored_tag_but_allows_newer_tag() = runTest {
        val fakeClient = FakeGitHubReleaseClient(
            releaseJson(
                tagName = "v3.2.6",
                "Mistysound-3.2.6-public-release.apk" to
                    "https://example.com/public.apk",
            )
        )
        val checker = UpdateChecker(dataStore, fakeClient)
        var currentDay = "2026-05-03"
        checker.currentDayProvider = { currentDay }
        checker.ignoreUpdate("v3.2.6")

        assertThat(checker.checkAutomatically("3.2.5"))
            .isEqualTo(UpdateCheckResult.Ignored)

        currentDay = "2026-05-04"
        fakeClient.releaseJson = releaseJson(
            tagName = "v3.2.7",
            "Mistysound-3.2.7-public-release.apk" to
                "https://example.com/public-327.apk",
        )

        val newerResult = checker.checkAutomatically("3.2.5")
        assertThat(newerResult)
            .isInstanceOf(UpdateCheckResult.UpdateAvailable::class.java)
        val update = (newerResult as UpdateCheckResult.UpdateAvailable).update
        assertThat(update.tagName).isEqualTo("v3.2.7")
        assertThat(update.downloadUrl).isEqualTo("https://example.com/public-327.apk")
    }

    @Test fun automatic_check_failure_does_not_record_day() = runTest {
        val fakeClient = FakeGitHubReleaseClient(
            releaseJson(
                tagName = "v3.2.6",
                "Mistysound-3.2.6-public-release.apk" to
                    "https://example.com/public.apk",
            ),
            shouldFail = true,
        )
        val checker = UpdateChecker(dataStore, fakeClient)
        checker.currentDayProvider = { "2026-05-03" }

        assertThat(checker.checkAutomatically("3.2.5"))
            .isEqualTo(UpdateCheckResult.Failed)
        assertThat(dataStore.data.first()[lastAutoUpdateCheckDayKey]).isNull()

        assertThat(checker.checkAutomatically("3.2.5"))
            .isEqualTo(UpdateCheckResult.Failed)
        assertThat(fakeClient.callCount).isEqualTo(2)
    }

    private class FakeGitHubReleaseClient(
        var releaseJson: String,
        var shouldFail: Boolean = false,
    ) : GitHubReleaseClient() {
        var callCount = 0
            private set

        override suspend fun latestReleaseJson(): String {
            callCount++
            if (shouldFail)
                error("Fake update check failure")
            return releaseJson
        }
    }

    private fun releaseJson(
        tagName: String,
        vararg assets: Pair<String, String>,
    ) = """
        {
          "tag_name": "$tagName",
          "draft": false,
          "prerelease": false,
          "assets": [
            ${assets.joinToString(",\n") { (name, url) ->
                """{"name": "$name", "browser_download_url": "$url"}"""
            }}
          ]
        }
    """.trimIndent()
}
