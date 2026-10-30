package app.stak.nativeapp.data

fun compactQualityFacts(report:QualityReport):List<QualityFact>{
    fun group(key:String,label:String,keys:List<String>):QualityFact? {
        val known=report.facts.filter{it.key in keys&&!it.value.isNullOrBlank()};if(known.isEmpty())return null
        val value=keys.mapNotNull{k->known.firstOrNull{it.key==k}?.value?.lineSequence()?.take(3)?.joinToString(" · ")}.distinct().joinToString(" · ")
        val confidence=when{known.any{it.confidence==FactConfidence.INFERRED}->FactConfidence.INFERRED;known.any{it.confidence==FactConfidence.REPORTED}->FactConfidence.REPORTED;else->FactConfidence.VERIFIED}
        return QualityFact(key,label,value,confidence)
    }
    return listOfNotNull(group("video","Video",listOf("resolution","codec","hdr")),group("audioSummary","Audio",listOf("audio","spatial","audioLanguage","channels")),group("subtitleSummary","Subtitles",listOf("subtitles")),group("file","File",listOf("size","container","duration")),report.facts.firstOrNull{it.key=="availability"&&it.value!=null})
}
fun matchingTorBoxOwner(entry:DavEntry,torrents:List<TorBoxTorrent>):TorBoxTorrent? {
    val matches=torrents.filter{torrent->torrent.files.any{file->file.size>0&&entry.size==file.size&&file.name.replace('\\','/').substringAfterLast('/') in setOf(entry.name,entry.relativePath.substringAfterLast('/'))}}
    val hashScoped=matches.filter{it.hash!=null&&Regex("(?i)(?<![a-f0-9])${Regex.escape(it.hash)}(?![a-f0-9])").containsMatchIn(entry.relativePath)}
    return hashScoped.singleOrNull() ?: matches.singleOrNull()
}

fun compactQualityConfidence(report:QualityReport,key:String):String {
    val keys=when(key){"video"->setOf("resolution","codec","hdr");"audioSummary"->setOf("audio","spatial","audioLanguage","channels");"subtitleSummary"->setOf("subtitles");"file"->setOf("size","container","duration");else->setOf(key)}
    return report.facts.filter{it.key in keys&&it.value!=null}.map{it.confidence.name}.distinct().joinToString(" / ")
}
