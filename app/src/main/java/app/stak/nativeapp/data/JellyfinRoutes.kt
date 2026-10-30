package app.stak.nativeapp.data

/** Documented legacy-to-current routes. Retry only a definitive HTTP 404. */
fun legacyJellyfinRoute(path:String):Pair<String,String>? {
    val match=Regex("^/Users/([A-Za-z0-9-]{1,128})/(.+)$").matchEntire(path) ?: return null
    val user=match.groupValues[1];val tail=match.groupValues[2]
    val route=when {
        tail=="Items"->"/Items"
        tail=="Views"->"/UserViews"
        tail=="Items/Resume"->"/UserItems/Resume"
        Regex("Items/[A-Za-z0-9-]{1,128}/UserData").matches(tail)->"/UserItems/"+tail.removePrefix("Items/")
        Regex("Items/[A-Za-z0-9-]{1,128}").matches(tail)->"/"+tail
        Regex("PlayedItems/[A-Za-z0-9-]{1,128}").matches(tail)->"/User"+tail
        Regex("FavoriteItems/[A-Za-z0-9-]{1,128}").matches(tail)->"/User"+tail
        else->return null
    }
    return route to user
}
