package app.stak.nativeapp

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** Portable authenticated encryption. The password and derived key are never persisted. */
object EncryptedBackup {
    const val MAX_FILE_BYTES = 4 * 1024 * 1024
    const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
    private const val ITERATIONS = 310_000
    private const val FORMAT = "stak-encrypted-backup"
    private val aad = "$FORMAT:1:PBKDF2-HMAC-SHA256:AES-256-GCM:$ITERATIONS".toByteArray()

    fun isEncrypted(text: String): Boolean = JSONObject(text).optString("format") == FORMAT

    private fun key(password: CharArray, salt: ByteArray): ByteArray {
        require(password.isNotEmpty() && password.size <= 1024) {
            "Enter a backup password (up to 1,024 characters)."
        }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(payload: String, password: CharArray): String {
        require(password.size >= 12) { "Use a backup password of at least 12 characters." }
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Backup exceeds 2 MB." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secret = key(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(secret, "AES"),
                GCMParameterSpec(128, iv),
            )
            cipher.updateAAD(aad)
            val encode = Base64.getEncoder()
            return JSONObject()
                .put("format", FORMAT)
                .put("version", 1)
                .put("kdf", "PBKDF2-HMAC-SHA256")
                .put("iterations", ITERATIONS)
                .put("cipher", "AES-256-GCM")
                .put("salt", encode.encodeToString(salt))
                .put("iv", encode.encodeToString(iv))
                .put("payload", encode.encodeToString(cipher.doFinal(bytes)))
                .toString(2)
        } finally {
            secret.fill(0)
            bytes.fill(0)
        }
    }

    fun decrypt(text: String, password: CharArray): String {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Backup file exceeds 4 MB."
        }
        val root = JSONObject(text)
        require(
            root.optString("format") == FORMAT &&
                root.optInt("version") == 1 &&
                root.optString("kdf") == "PBKDF2-HMAC-SHA256" &&
                root.optInt("iterations") == ITERATIONS &&
                root.optString("cipher") == "AES-256-GCM"
        ) {
            "Unsupported encrypted backup."
        }
        val decode = Base64.getDecoder()
        val salt = decode.decode(root.getString("salt"))
        val iv = decode.decode(root.getString("iv"))
        val payload = decode.decode(root.getString("payload"))
        require(salt.size == 16 && iv.size == 12 && payload.size in 16..(MAX_PAYLOAD_BYTES + 16)) {
            "Invalid encrypted backup."
        }
        val secret = key(password, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(secret, "AES"),
                GCMParameterSpec(128, iv),
            )
            cipher.updateAAD(aad)
            val plain =
                try {
                    cipher.doFinal(payload)
                } catch (_: java.security.GeneralSecurityException) {
                    throw IllegalArgumentException(
                        "Incorrect password or damaged backup. Nothing was imported."
                    )
                }
            return try {
                String(plain, Charsets.UTF_8)
            } finally {
                plain.fill(0)
            }
        } finally {
            secret.fill(0)
        }
    }
}
