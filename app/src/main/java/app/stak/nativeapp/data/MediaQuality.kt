package app.stak.nativeapp.data

import com.google.gson.JsonObject

enum class FactConfidence{VERIFIED,REPORTED,INFERRED,UNKNOWN}
data class QualityFact(val key:String,val label:String,val value:String?,val confidence:FactConfidence=FactConfidence.UNKNOWN)
data class QualityReport(val title:String,val facts:List<QualityFact>,val origin:String){fun value(key:String)=facts.firstOrNull{it.key==key}?.value}
private val qualityLabels=linkedMapOf("resolution" to "Resolution","source" to "Source","codec" to "Video codec","profile" to "Video profile","depth" to "Bit depth","fps" to "Frame rate","bitrate" to "Bitrate","hdr" to "HDR format","primaries" to "Color primaries","transfer" to "Transfer","audio" to "Audio","channels" to "Channels","audioLanguage" to "Audio language","audioBitrate" to "Audio bitrate","spatial" to "Spatial audio","subtitles" to "Subtitles","container" to "Container","duration" to "Duration","size" to "File size","release" to "Release name")
fun inferredQuality(name:String,size:Long?=null,origin:String="Filename"):QualityReport {
    fun found(regex:String)=Regex(regex,RegexOption.IGNORE_CASE).find(name)?.value
    val values=mapOf("source" to when{found("\\bREMUX\\b")!=null->"REMUX";found("\\bBlu[ ._-]?Ray\\b")!=null->"BluRay encode";found("\\bWEB[ ._-]?DL\\b")!=null->"WEB-DL";found("\\bWEB[ ._-]?Rip\\b")!=null->"WEBRip";found("\\bHDTV\\b")!=null->"HDTV";else->null},"resolution" to (found("\\b(?:2160|1080|720|480)p\\b") ?: found("\\b4K\\b")),"codec" to when{found("\\b(?:HEVC|H[ ._-]?265|x265)\\b")!=null->"HEVC / H.265";found("\\b(?:AVC|H[ ._-]?264|x264)\\b")!=null->"AVC / H.264";found("\\bAV1\\b")!=null->"AV1";else->null},"hdr" to listOfNotNull(found("\\b(?:Dolby[ ._-]?Vision|DOVI|DV)\\b")?.let{"Dolby Vision"},found("\\bHDR10\\+(?!\\w)")?.let{"HDR10+"},found("\\bHDR10(?!\\+)")?.let{"HDR10"},found("\\bHDR\\b")?.let{"HDR (unspecified)"},found("\\bSDR\\b")?.let{"SDR"}).distinct().joinToString(" + ").ifBlank{null},"depth" to found("\\b(?:8|10|12)[ ._-]?bit\\b"),"audio" to found("\\b(?:TrueHD|DTS[ ._-]?HD[ ._-]?MA|DTS|E[ ._-]?AC[ ._-]?3|DDP|AC[ ._-]?3|AAC|FLAC)\\b"),"spatial" to listOfNotNull(found("\\bAtmos\\b"),found("\\bDTS[ ._-]?(?:X|XLL)\\b")).joinToString(" · ").ifBlank{null},"channels" to found("(?<!\\d)[257][ .][01](?!\\d)"),"audioLanguage" to Regex("\\b(?:HUN|ENG|Hungarian|English|MULTI)\\b",RegexOption.IGNORE_CASE).findAll(name).map{it.value.uppercase()}.distinct().joinToString(" · ").ifBlank{null},"container" to found("\\.(?:mkv|mp4|m4v|avi|ts|webm)$")?.removePrefix(".")?.uppercase(),"release" to name.take(500))
    return QualityReport(name,qualityLabels.map{(key,label)->val value=if(key=="size")size?.takeIf{it>0}?.let{"%.2f GiB".format(java.util.Locale.US,it/1073741824.0)} else values[key];QualityFact(key,label,value,when{value==null->FactConfidence.UNKNOWN;key=="size"->FactConfidence.REPORTED;else->FactConfidence.INFERRED})},origin)
}
fun verifiedQuality(item:JsonObject):QualityReport {
    val source=item.objects("MediaSources").firstOrNull();val streams=(source?.objects("MediaStreams").orEmpty()+item.objects("MediaStreams")).distinctBy{it.text("Type").orEmpty()+":"+it.text("Index")}
    val video=streams.firstOrNull{it.text("Type")=="Video"};val audio=streams.filter{it.text("Type")=="Audio"};val firstAudio=audio.firstOrNull{it.get("IsDefault")?.asBoolean==true} ?: audio.firstOrNull()
    fun rate(value:Double?)=value?.takeIf{it>0}?.let{"%.2f Mb/s".format(java.util.Locale.US,it/1_000_000)}
    val values=mapOf("resolution" to video?.number("Height")?.toInt()?.let{h->video.number("Width")?.toInt()?.let{"$it × $h"} ?: "${h}p"},"codec" to video?.text("Codec")?.uppercase(),"profile" to video?.text("Profile"),"depth" to video?.text("BitDepth")?.let{"$it bit"},"fps" to (video?.number("AverageFrameRate") ?: video?.number("RealFrameRate"))?.let{"%.3f fps".format(java.util.Locale.US,it)},"bitrate" to rate(video?.number("BitRate") ?: source?.number("Bitrate")),"hdr" to (video?.text("VideoRangeType") ?: video?.text("VideoRange")),"primaries" to video?.text("ColorPrimaries"),"transfer" to video?.text("ColorTransfer"),"audio" to audio.mapNotNull{it.text("DisplayTitle") ?: it.text("Codec")?.uppercase()}.distinct().joinToString("\n").ifBlank{null},"channels" to (firstAudio?.text("ChannelLayout") ?: firstAudio?.text("Channels")),"audioLanguage" to audio.mapNotNull{it.text("Language")}.distinct().joinToString(" · ").ifBlank{null},"audioBitrate" to rate(firstAudio?.number("BitRate")),"spatial" to audio.mapNotNull{it.text("AudioSpatialFormat")}.distinct().joinToString(" · ").ifBlank{null},"subtitles" to streams.filter{it.text("Type")=="Subtitle"}.joinToString("\n"){listOfNotNull(it.text("Language") ?: "Unknown language",it.text("Codec"),if(it.get("IsExternal")?.asBoolean==true)"external" else null).joinToString(" · ")}.ifBlank{null},"container" to (source?.text("Container") ?: item.text("Container"))?.uppercase(),"duration" to (source?.number("RunTimeTicks") ?: item.number("RunTimeTicks"))?.takeIf{it>0}?.let{"${(it/600_000_000).toLong()} min"},"size" to source?.number("Size")?.takeIf{it>0}?.let{"%.2f GiB".format(java.util.Locale.US,it/1073741824.0)},"release" to (source?.text("Path")?.substringAfterLast('/') ?: source?.text("Name") ?: item.text("Name")))
    return QualityReport(item.text("Name") ?: "Jellyfin media",qualityLabels.map{(key,label)->QualityFact(key,label,values[key],if(values[key]!=null)FactConfidence.VERIFIED else FactConfidence.UNKNOWN)},"Jellyfin scanned metadata · not a live playback test")
}
fun compatibilityWarnings(report:QualityReport,device:PlaybackDevice):List<String> = buildList {
    if(!device.enabled)return@buildList
    val hdr=report.value("hdr").orEmpty();val codec=report.value("codec").orEmpty();val spatial=(report.value("spatial").orEmpty()+report.value("audio").orEmpty())
    fun caution(condition:Boolean,support:String,label:String){if(condition&&support!="Supported")add(if(support=="Unsupported")"$label is marked unsupported for ${device.name}. Transcoding or a fallback may be needed." else "$label support is not configured for ${device.name}.")}
    caution(hdr.contains("DOVI",true)||hdr.contains("Dolby",true),device.dolbyVision,"Dolby Vision")
    caution(hdr.contains("HDR10+",true),device.hdr10Plus,"HDR10+")
    caution(hdr.contains("HDR10",true),device.hdr10,"HDR10")
    caution(codec.contains("HEVC",true)||codec.contains("265"),device.hevc,"HEVC")
    caution(codec.contains("AV1",true),device.av1,"AV1")
    caution(spatial.contains("Atmos",true),device.atmos,"Atmos")
    caution(spatial.contains("DTS",true),device.dts,"DTS audio")
    if(report.value("subtitles")!=null)add("Subtitle format and client burn-in settings can affect direct play.")
    add("Actual Jellyfin client capabilities, TV/audio setup and playback settings determine direct play. Filename traits are not verified compatibility.")
}

fun sourceQualityReport(source:SourceRelease):QualityReport {
    val base=inferredQuality(source.releaseText ?: source.name,source.torrentSizeBytes ?: source.sizeBytes,"Source provider + filename; size is reported, not inspected")
    return base.copy(facts=base.facts+QualityFact("availability","Availability",if(source.cached)"Reported cached on TorBox"else "Not verified cached",if(source.cached)FactConfidence.REPORTED else FactConfidence.UNKNOWN))
}
