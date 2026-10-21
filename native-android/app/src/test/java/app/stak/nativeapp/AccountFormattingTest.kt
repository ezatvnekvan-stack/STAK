package app.stak.nativeapp

import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountFormattingTest {
    @Test
    fun missingAccountValuesStayUnknownAndBytesUseOriginalUnits() {
        assertEquals("—", accountBytes(JSONObject.NULL))
        assertEquals("0 B", accountBytes(0))
        assertEquals("1 KB", accountBytes(1024))
        assertEquals("954 MB", accountBytes(1_000_000_000))
        assertEquals("1.00 GB", accountBytes(1_073_741_824))
        assertEquals("1.00 TB", accountBytes(1_099_511_627_776L))
        assertEquals("Unknown", accountPlan(JSONObject.NULL))
        assertEquals("Free", accountPlan(0))
        assertEquals("Essential", accountPlan(1))
        assertEquals("Pro", accountPlan(2))
        assertEquals("Standard", accountPlan(3))
    }

    @Test
    fun accountExpiryKeepsOriginalUsLocaleAndDeviceTimeZone() {
        assertEquals("—", accountDate(null))
        assertEquals("Invalid Date", accountDate("invalid"))
        assertEquals("10/20/2026", accountDate("2026-10-20T01:00:00Z", ZoneId.of("UTC")))
        assertEquals(
            "10/19/2026",
            accountDate("2026-10-20T01:00:00Z", ZoneId.of("America/Los_Angeles")),
        )
    }
}
