package app.stak.nativeapp.data

/** Stable preference values; older backups and unknown values retain the original look. */
enum class BackgroundStyle(val key: String, val label: String) {
    Current("current", "Current"),
    Glyph("glyph", "Glyph");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Current
    }
}
