package app.mylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun newerVersions() {
        assertTrue(Updater.isNewer("v0.7.0", "0.6.0"))
        assertTrue(Updater.isNewer("v0.10.0", "0.9.3"))
        assertFalse(Updater.isNewer("v0.6.0", "0.6.0"))
        assertFalse(Updater.isNewer("v0.5.9", "0.6.0"))
        assertFalse(Updater.isNewer("0.6", "0.6.0"))
    }

    @Test
    fun notesKeepBullets() {
        val body = "Intro\n- **Uno**: primo\n- `due`\n\nfine"
        assertEquals("• Uno: primo\n• due", Updater.notesFor(body))
    }
}
