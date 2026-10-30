package app.stak.nativeapp.data

fun jellyfinIdentityKeys(item:MediaItem):Set<String> {
    val type=item.type.lowercase(java.util.Locale.ROOT)
    val keys=mutableSetOf("item:${item.id}")
    item.imdbId?.takeIf{it.matches(Regex("tt\\d+"))}?.let{keys+="imdb:$it"}
    item.mediaKey.takeIf{it.matches(Regex("tt\\d+"))}?.let{keys+="imdb:$it"}
    item.id.takeIf{it.matches(Regex("tmdb:\\d+"))}?.let{keys+=it}
    item.jellyfinId?.let{keys+="jellyfin:$it"}
    keys+=item.jellyfinProviderKeys.orEmpty()
    return keys.map{"$type:$it"}.toSet()
}
