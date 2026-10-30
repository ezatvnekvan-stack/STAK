package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

/** Only public Cinemeta responses enter this cache; no keys or account data. */
internal object PublicMetadataCache {
    var directory: File? = null
    private fun file(url: String): File? {
        if (!url.startsWith("https://v3-cinemeta.strem.io/")) return null
        val root = directory ?: return null
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(root, "$key.json")
    }
    @Synchronized fun read(url: String): JsonObject? = try {
        val file = file(url)
        if (file != null && file.exists() && System.currentTimeMillis() - file.lastModified() < 86_400_000L) JsonParser.parseString(file.readText()).asJsonObject else null
    } catch (_: Exception) { null }
    @Synchronized fun write(url: String, value: JsonObject) {
        try {
            val file = file(url) ?: return
            file.parentFile?.mkdirs()
            file.writeText(value.toString())
        } catch (_: Exception) { /* A full cache must not break a successful request. */ }
    }
}
