package app.stak.nativeapp.data

import com.google.gson.JsonParser

object NavigationPreferences {
    val defaults = listOf("home", "discover", "watchlist", "tracking", "library", "account")
    val allowed = defaults + "torbox"
    fun normalize(ids: List<String>): List<String> {
        val result = ids.map { if (it in listOf("history", "calendar", "progress")) "tracking" else if (it == "series") "library" else it }.filter { it in allowed }.distinct().toMutableList()
        listOf("home", "account").forEach { if (it !in result) result.add(it) }
        return result
    }
    fun read(json: String?): List<String> = try {
        if (json.isNullOrBlank()) defaults else normalize(JsonParser.parseString(json).asJsonArray.map { it.asString })
    } catch (_: RuntimeException) { defaults }
}
