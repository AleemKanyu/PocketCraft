package com.pockethost.app.config

import com.pockethost.app.BuildConfig

/**
 * Whether this build may download server software itself.
 *
 * Google Play's Device and Network Abuse policy forbids an app from downloading code
 * (JARs here) that it then runs. The Play build therefore never fetches a server JAR,
 * plugin or mod: it opens the official page in the user's browser and imports the file
 * they pick. The side-loaded build is not bound by that policy and downloads in place.
 *
 * Every function that saves a downloaded JAR checks this first, so a screen that
 * forgets to branch still cannot break the rule.
 */
object DownloadPolicy {
    val inAppCodeDownloads: Boolean = BuildConfig.IS_EXTERNAL_BUILD

    const val BLOCKED_MESSAGE =
        "This version cannot download server files itself. Download the file in your browser, then import it."

    class Blocked : IllegalStateException(BLOCKED_MESSAGE)
}
