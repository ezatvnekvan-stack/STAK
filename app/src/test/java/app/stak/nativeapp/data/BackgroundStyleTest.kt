package app.stak.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class BackgroundStyleTest {
    @Test fun oldOrUnknownPreferencesKeepCurrentBackground() {
        listOf(null, "", "future-style", "GLYPH").forEach {
            assertEquals(BackgroundStyle.Current, BackgroundStyle.fromKey(it))
        }
    }
    @Test fun stablePreferenceKeysRoundTrip() {
        BackgroundStyle.entries.forEach { assertEquals(it, BackgroundStyle.fromKey(it.key)) }
    }
    @Test fun backgroundChoiceBelongsInExistingEncryptedBackup() {
        assertTrue(cloudBackupKey("background_style"))
    }
}
