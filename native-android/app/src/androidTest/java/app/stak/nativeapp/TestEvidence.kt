package app.stak.nativeapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Writes a test screenshot into the shared evidence collection.
 *
 * Direct writes to /sdcard/Pictures are denied to the app on API 29+; MediaStore is the supported
 * path and also keeps the evidence visible to `adb pull`.
 */
fun saveEvidence(name: String, bitmap: Bitmap) {
    val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/STAK-native-evidence")
        }
    val uri =
        requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Could not create evidence file $name"
        }
    requireNotNull(resolver.openOutputStream(uri)) { "Could not open evidence file $name" }.use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) {
            "Could not encode evidence file $name"
        }
    }
}
