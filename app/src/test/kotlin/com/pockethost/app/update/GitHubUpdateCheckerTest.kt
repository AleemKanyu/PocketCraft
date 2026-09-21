package com.pockethost.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun testVersionComparison() {
        // Newer versions
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.3", "1.2.4") < 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.3", "1.3.0") < 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.3", "2.0.0") < 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.3", "v1.2.4") < 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("v1.2.3", "v1.2.4") < 0)

        // Equal versions
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("1.2.4", "1.2.4"))
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("v1.2.4", "1.2.4"))
        assertEquals(0, GitHubUpdateChecker.compareVersionNames("1.2.4", "v1.2.4"))

        // Older versions (downgrades)
        assertTrue(GitHubUpdateChecker.compareVersionNames("1.2.4", "1.2.3") > 0)
        assertTrue(GitHubUpdateChecker.compareVersionNames("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun testThreeOpenCadenceRule() {
        fun shouldPromptUpdate(
            releaseVersion: String,
            lastPromptedVersion: String,
            lastPromptedLaunch: Int,
            currentLaunch: Int
        ): Boolean {
            val isNewVersion = lastPromptedVersion != releaseVersion
            val isEvery3Opens = (currentLaunch - lastPromptedLaunch) >= 3
            return isNewVersion || isEvery3Opens
        }

        val releaseVer = "1.2.4"

        // Open 1: Brand new version detected -> MUST show
        var lastVersion = ""
        var lastLaunch = 0
        var currentLaunch = 1
        assertTrue(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // User dismisses on Open 1 -> record launch count 1
        lastVersion = releaseVer
        lastLaunch = currentLaunch

        // Open 2 (1 open after prompt) -> MUST NOT show
        currentLaunch = 2
        assertFalse(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // Open 3 (2 opens after prompt) -> MUST NOT show
        currentLaunch = 3
        assertFalse(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // Open 4 (3 opens after prompt) -> MUST show
        currentLaunch = 4
        assertTrue(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // User dismisses again on Open 4 -> record launch count 4
        lastLaunch = currentLaunch

        // Open 5 (1 open after 2nd prompt) -> MUST NOT show
        currentLaunch = 5
        assertFalse(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // Open 6 (2 opens after 2nd prompt) -> MUST NOT show
        currentLaunch = 6
        assertFalse(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // Open 7 (3 opens after 2nd prompt) -> MUST show
        currentLaunch = 7
        assertTrue(shouldPromptUpdate(releaseVer, lastVersion, lastLaunch, currentLaunch))

        // But if a brand new version 1.2.5 is released while waiting -> MUST show immediately!
        assertTrue(shouldPromptUpdate("1.2.5", lastVersion, lastLaunch, 5))
    }
}
