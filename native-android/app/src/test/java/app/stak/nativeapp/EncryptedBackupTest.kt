package app.stak.nativeapp

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EncryptedBackupTest {
    private val password
        get() = "test-only strong backup password".toCharArray()

    @Test
    fun roundTripAuthenticatesPrivateDataAndUsesFreshRandomness() {
        val plain = "{\"private\":\"TEST_ONLY_KEY\"}"
        val first = EncryptedBackup.encrypt(plain, password)
        val second = EncryptedBackup.encrypt(plain, password)
        assertFalse(first.contains("TEST_ONLY_KEY"))
        assertNotEquals(first, second)
        assertEquals(plain, EncryptedBackup.decrypt(first, password))
    }

    @Test
    fun wrongPasswordNeverReturnsPlaintext() {
        val text = EncryptedBackup.encrypt("secret", password)
        val failure =
            assertThrows(IllegalArgumentException::class.java) {
                EncryptedBackup.decrypt(text, "wrong".toCharArray())
            }
        assertTrue(failure.message!!.contains("Nothing was imported"))
    }

    @Test
    fun modifiedCiphertextAndNonceAreRejected() {
        for (field in listOf("payload", "iv", "salt")) {
            val root = JSONObject(EncryptedBackup.encrypt("secret", password))
            val bytes = Base64.getDecoder().decode(root.getString(field))
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            root.put(field, Base64.getEncoder().encodeToString(bytes))
            assertThrows(IllegalArgumentException::class.java) {
                EncryptedBackup.decrypt(root.toString(), password)
            }
        }
    }

    @Test
    fun unknownOrExpensiveKdfIsRejectedBeforeDerivation() {
        val root =
            JSONObject(EncryptedBackup.encrypt("secret", password)).put("iterations", Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.decrypt(root.toString(), password)
        }
        root.put("iterations", 310000).put("version", 900)
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.decrypt(root.toString(), password)
        }
    }

    @Test
    fun weakPasswordsAndOversizedPayloadAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.encrypt("secret", "short".toCharArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.encrypt("x".repeat(EncryptedBackup.MAX_PAYLOAD_BYTES + 1), password)
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackup.decrypt("x".repeat(EncryptedBackup.MAX_FILE_BYTES + 1), password)
        }
    }
}
