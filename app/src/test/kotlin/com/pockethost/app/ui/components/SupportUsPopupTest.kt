package com.pockethost.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportUsPopupTest {

    @Test
    fun `due on every fourth launch only`() {
        assertFalse(SupportUsPopup.isDue(launchCount = 0, lastShownLaunchCount = 0))
        assertFalse(SupportUsPopup.isDue(launchCount = 3, lastShownLaunchCount = 0))
        assertTrue(SupportUsPopup.isDue(launchCount = 4, lastShownLaunchCount = 0))
        assertFalse(SupportUsPopup.isDue(launchCount = 5, lastShownLaunchCount = 4))
        assertTrue(SupportUsPopup.isDue(launchCount = 8, lastShownLaunchCount = 4))
    }

    @Test
    fun `not shown twice in the same launch`() {
        assertFalse(SupportUsPopup.isDue(launchCount = 4, lastShownLaunchCount = 4))
    }
}
