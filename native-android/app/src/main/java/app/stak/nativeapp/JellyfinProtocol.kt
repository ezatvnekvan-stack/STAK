package app.stak.nativeapp

import okhttp3.HttpUrl.Companion.toHttpUrl

/** Send the standard authorization header even before a session token exists. */
fun jellyfinAuthorization(token: String = ""): String {
    require(token.length <= 4096 && token.all { it.code in 32..126 }) { "Invalid Jellyfin credential." }
    val identity = "MediaBrowser Client=\"STAK Native\", Device=\"Android\", DeviceId=\"stak-native\", Version=\"${BuildConfig.VERSION_NAME}\""
    return if (token.isBlank()) identity
    else "$identity, Token=\"${token.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

fun normalizeJellyfinAddress(address: String): String {
    require(Regex("https?://.*", RegexOption.IGNORE_CASE).matches(address.trim())) {
        "Enter an http:// or https:// Jellyfin server address."
    }
    val parsed = address.trim().toHttpUrl()
    require(parsed.username.isBlank() && parsed.password.isBlank()) { "Enter only your server address, without credentials." }
    val web = Regex("/web(?:/index\\.html)?/?$", RegexOption.IGNORE_CASE).find(parsed.encodedPath)
    require(web != null || (parsed.query == null && parsed.fragment == null)) { "Enter only your Jellyfin server address." }
    return parsed.newBuilder().apply {
        if (web != null) encodedPath(parsed.encodedPath.substring(0, web.range.first).ifEmpty { "/" })
        query(null)
        fragment(null)
    }.build().toString().trimEnd('/')
}
