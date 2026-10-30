package app.stak.nativeapp.data

private val uhd=Regex("(?i)2160p|\\b4K\\b")
private val fullHd=Regex("(?i)1080p")
private val hd=Regex("(?i)720p")
private val sd=Regex("(?i)480p")
private val damaged=Regex("(?i)\\b(?:CAM|HDCAM|HDTS|TELESYNC|TELECINE|SCR|SCREENER)\\b")
private val remuxName=Regex("(?i)\\bREMUX\\b")
private val discName=Regex("(?i)BLU[ -]?RAY|BDRIP|BDMV|BDREMUX")
private val webName=Regex("(?i)WEB[ -]?DL|\\bWEB\\b")
private val broadcastName=Regex("(?i)WEBRIP|HDTV")
private val losslessName=Regex("(?i)TRUE[ .-]?HD|DTS[ .-]?(?:HD|X)|\\bFLAC\\b|\\bPCM\\b")
fun sourceResolutionRank(source:SourceRelease):Int {
    val text=source.resolution+" "+source.name
    return when{uhd.containsMatchIn(text)->4;fullHd.containsMatchIn(text)->3;hd.containsMatchIn(text)->2;sd.containsMatchIn(text)->1;else->0}
}
private fun sourceTier(source:SourceRelease):Int {
    val name=source.name.replace('.', ' ').replace('_',' ')
    if(damaged.containsMatchIn(name))return -1
    if(source.remux||remuxName.containsMatchIn(name))return 5
    if(discName.containsMatchIn(name))return 4
    if(source.webdl||webName.containsMatchIn(name))return 3
    if(broadcastName.containsMatchIn(name))return 2
    return 1
}
private fun losslessAudio(source:SourceRelease)=losslessName.containsMatchIn(source.name)
private data class QualityKey(val source:SourceRelease,val clean:Int,val resolution:Int,val tier:Int,val dynamicRange:Int,val audio:Int,val atmos:Int,val cached:Int,val hun:Int)
fun bestQualityOrder(items:List<SourceRelease>,cached:Set<String>,preferHun:Boolean):List<SourceRelease> = items.map{source->
    val tier=sourceTier(source)
    QualityKey(source,if(tier<0)0 else 1,sourceResolutionRank(source),tier,if(source.dolbyVision)2 else if(source.hdr)1 else 0,if(losslessAudio(source))1 else 0,if(source.atmos)1 else 0,if(source.infoHash in cached)1 else 0,if(preferHun&&"HUN" in source.languages)1 else 0)
}.sortedWith(compareByDescending<QualityKey>{it.clean}.thenByDescending{it.resolution}.thenByDescending{it.tier}.thenByDescending{it.dynamicRange}.thenByDescending{it.audio}.thenByDescending{it.atmos}.thenByDescending{it.cached}.thenByDescending{it.hun}).map{it.source}
fun sourceQualitySummary(source:SourceRelease):String=listOfNotNull(
    when(sourceResolutionRank(source)){4->"4K";3->"1080p";2->"720p";1->"480p";else->null},
    if(sourceTier(source)==5)"Remux" else if(sourceTier(source)==4)"Blu-ray" else if(source.webdl)"WEB-DL" else null,
    if(source.dolbyVision)"Dolby Vision" else if(source.hdr)"HDR" else null,
    if(losslessAudio(source)&&source.atmos)"Lossless Atmos" else if(losslessAudio(source))"Lossless audio" else if(source.atmos)"Atmos" else null
).joinToString(" · ")
