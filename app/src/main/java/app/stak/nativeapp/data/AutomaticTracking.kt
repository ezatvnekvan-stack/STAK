package app.stak.nativeapp.data

/** Current state comes from this Jellyfin user, never legacy manual rows or queued edits. */
fun serverTrackingStates(scope:String,rows:List<JournalEntry>,items:List<MediaItem> = emptyList(),observedAt:Long=0):List<JournalEntry>{
    if(scope=="local")return emptyList()
    val remote=rows.filter{it.scope==scope&&it.source.equals("Jellyfin",true)}
    val observed=items.filter{it.jellyfinId!=null&&it.jellyfinUserDataKnown==true}.map{JournalEntry(it,it.lastPlayedAt.takeIf{n->n>0},it.watched,"Jellyfin",observedAt,scope)}
    return (remote+observed).sortedByDescending{it.updatedAt}.fold(emptyList()){all,row->if(all.any{matchingWatch(it.media,row.media)})all else all+row}
}
fun serverViewingHistory(scope:String,events:List<WatchEvent>,states:List<JournalEntry>):List<JournalEntry>{
    if(scope=="local")return emptyList()
    val current=states.filter{it.scope==scope&&it.source.equals("Jellyfin",true)}.sortedByDescending{it.updatedAt}
    val history=events.filter{it.scope==scope&&it.source.equals("Jellyfin",true)}.map{event->
        val fresh=current.firstOrNull{matchingWatch(it.media,event.media)}?.media
        val media=if(fresh==null)event.media else event.media.copy(
            poster=fresh.poster?.takeIf{it.isNotBlank()}?:event.media.poster,
            background=fresh.background?.takeIf{it.isNotBlank()}?:event.media.background)
        event.journal().copy(media=media)
    }
    return (history+states.filter{it.scope==scope&&it.played}).fold(emptyList<JournalEntry>()){all,row->
        if(all.any{matchingWatch(it.media,row.media)&&it.watchedAt==row.watchedAt})all else all+row
    }.sortedByDescending{it.watchedAt?:Long.MIN_VALUE}
}
fun automaticWatchingRows(plans:List<SeriesProgress>,states:List<JournalEntry>,feed:List<MediaItem>):List<WatchingRow>{
    val real=feed.filter{item->item.jellyfinId!=null&&!item.watched&&states.none{it.played&&matchingWatch(it.media,item)}}
    val normal=plans.filter{p->states.any{it.played&&it.isEpisode&&sameSeries(it.media,p.plan.media)}||real.any{it.isSeries&&sameSeries(it,p.plan.media)}}.map{it.copy(plan=it.plan.copy(status="watching"),status=if(it.complete)"completed"else "watching")}
    val rows=watchingRows(normal,states,real,emptyList()).toMutableList()
    real.filter{it.isSeries&&it.nextUp}.forEach{item->if(rows.none{sameSeries(it.media,item)}){
        val plan=normal.firstOrNull{sameSeries(it.plan.media,item)}
        val episode=item.season?.let{s->item.episode?.let{e->Episode(s,e,item.name,runtime=item.runtime)}}
        rows+=WatchingRow(plan?.plan?.media?:journalSeriesTitle(item),series=plan,resume=item.takeIf{it.positionMs>0},episode=episode,lastActivity=item.lastPlayedAt)
    }}
    return rows.sortedByDescending{it.lastActivity}
}

data class TrackingBin(val start:java.time.LocalDate,val end:java.time.LocalDate,val count:Int)
fun trackingTimeline(entries:List<JournalEntry>,period:String,today:java.time.LocalDate):List<TrackingBin>{
    val dates=entries.filter{it.played}.mapNotNull{journalDay(it.watchedAt)}.filter{!it.isAfter(today)}
    if(period=="all"){
        val month=java.time.YearMonth.from(today)
        return (11L downTo 0L).map{n->val m=month.minusMonths(n);val start=m.atDay(1);val end=minOf(m.atEndOfMonth(),today);TrackingBin(start,end,dates.count{!it.isBefore(start)&&!it.isAfter(end)})}
    }
    val start=when(period){"week"->today.minusDays(6);"month"->today.minusDays(29);else->today.withDayOfMonth(1)}
    return (0L..java.time.temporal.ChronoUnit.DAYS.between(start,today)).map{n->val date=start.plusDays(n);TrackingBin(date,date,dates.count{it==date})}
}

/** Narrow guard: scans, favorites and session controls stay available; playback history writes do not. */
fun jellyfinTrackingMutationAllowed(path:String,method:String):Boolean {
    if(method.equals("GET",true)||method.equals("HEAD",true))return true
    val clean=path.substringBefore('?').trimEnd('/').lowercase()
    return !clean.contains("/playeditems/")&&!clean.contains("/userplayeditems/")&&!clean.endsWith("/userdata")
}
