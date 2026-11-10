package app.stak.nativeapp.data

import java.time.LocalDate
import java.time.DayOfWeek

/** Presentation only: never enables a rule or performs acquisition. Paused rules remain followed. */
fun followingMedia(rules:List<WatchRuleModel>):List<MediaItem> = rules.filter{it.enabled&&it.pendingAction!="off"}.mapNotNull{r->
    val id=r.seriesImdb?:r.seriesTmdb?.let{"tmdb:$it"}?:return@mapNotNull null
    MediaItem(id=id,imdbId=r.seriesImdb,type="series",name=r.seriesName,poster=r.poster,jellyfinSeriesId=r.seriesJellyfin)
}.distinctBy{it.mediaKey}

fun followingUpcomingEvents(feed:ReleaseFeed,rules:List<WatchRuleModel>,today:LocalDate):List<ReleaseEvent>{
    val selected=rules.filter{it.enabled&&it.pendingAction!="off"}
    val allowed=followingMedia(selected).map{it.mediaKey}.toSet()
    return feed.events.filter{event->event.media.isSeries&&event.media.mediaKey in allowed&&event.season!=null&&event.episode!=null&&
        selected.any{rule->((rule.seriesImdb?:rule.seriesTmdb?.let{"tmdb:$it"})==event.media.mediaKey)&&
            (rule.watchedSeasons.isEmpty()||event.season in rule.watchedSeasons)&&(event.season!=0||rule.includeSpecials)}}
        .filter{!it.date.isBefore(today)&&!it.date.isAfter(today.plusDays(30))}.distinctBy{it.key}
        .sortedWith(compareBy<ReleaseEvent>{it.date}.thenBy{it.media.name}.thenBy{it.episode}).take(8)
}
fun followingSummary(rule:WatchRuleModel,status:WatchRuleStatus?,episodes:List<WatchEpisode>):String=when{
    rule.pendingAction=="off"->"Stop pending confirmation"
    rule.state!=WatchRuleModel.STATE_SERVER_CONFIRMED->"Saved · waiting for sync"
    rule.paused->"Automatic additions paused"
    watchNeedsAttention(rule,status)->"Needs attention · open for details"
    else->{val counts=if(episodes.isNotEmpty())episodes.groupingBy{it.status}.eachCount()else status?.statuses.orEmpty()
        val added=listOf("available_jellyfin","waiting_library","added_torbox").sumOf{counts[it]?:0}
        val future=counts["waiting_release"]?:0
        if(counts.isEmpty())"Waiting for first update"else "$added added · $future awaiting release"}
}
fun sourceQualityHeading(s:SourceRelease):String=listOf(if(s.resolution=="2160p")"4K"else s.resolution.uppercase(),when{ s.remux->"Remux";s.webdl->"WEB-DL";else->null}).filterNotNull().joinToString(" · ")
fun releaseBadges(s:SourceRelease):List<String> = buildList{
    add(if(s.resolution=="2160p")"4K"else s.resolution.uppercase())
    if(s.dolbyVision)add("Dolby Vision")
    if(s.hdr)add(if(Regex("HDR10[+]|HDR10PLUS",RegexOption.IGNORE_CASE).containsMatchIn(s.name))"HDR10+"else "HDR")
    if(s.atmos)add("Atmos")
    if(s.remux)add("REMUX")else if(s.webdl)add("WEB-DL")
}
data class ViewingActivityDay(val date:LocalDate,val entries:List<JournalEntry>){val count get()=entries.size}
/** Twelve Monday-aligned weeks including today. Future cells are rendered disabled. */
fun activityDays(entries:List<JournalEntry>,today:LocalDate,end:LocalDate=today):List<ViewingActivityDay>{
    val last=end.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val first=last.minusDays(83)
    val grouped=individualWatches(entries).filter{it.played}.mapNotNull{e->journalDay(e.watchedAt)?.takeIf{!it.isAfter(today)}?.let{it to e}}.groupBy({it.first},{it.second})
    return (0L..83L).map{d->val day=first.plusDays(d);ViewingActivityDay(day,grouped[day].orEmpty())}
}
