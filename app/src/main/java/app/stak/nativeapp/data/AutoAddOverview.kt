package app.stak.nativeapp.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** Local defaults contain quality choices only, never consent or a series identity. */
data class WatchQualityProfile(
    val resolution:String=WatchModes.RESOLUTION_2160_PREFERRED,
    val waitHours:Int=24,
    val releases:List<String> = listOf(WatchModes.RELEASE_WEBDL),
    val hdr:String=WatchModes.MODE_ANY,
    val dv:String=WatchModes.MODE_ANY,
    val atmos:String=WatchModes.MODE_ANY,
    val language:String=WatchModes.LANG_ANY,
    val maxGb:Double?=null,
    val checkMinutes:Int=45
)

data class WatchEpisode(val season:Int,val number:Int,val status:String,val detail:String,
                        val lastCheck:Long,val nextCheck:Long,val airDate:String?){
    val label:String get()="S${season.toString().padStart(2,'0')}E${number.toString().padStart(2,'0')}"
}

internal fun watchEpisodes(rows:JsonArray):List<WatchEpisode> = rows.mapNotNull { element ->
    runCatching {
        val o=element.asJsonObject
        fun seconds(key:String)=o.get(key)?.takeUnless{it.isJsonNull}?.asDouble?.times(1000)?.toLong()?:0L
        val season=o.get("season")?.asInt?:return@runCatching null
        val number=o.get("episode_number")?.asInt?:return@runCatching null
        WatchEpisode(season,number,o.get("status")?.asString?:"unknown",
            o.get("status_detail")?.takeUnless{it.isJsonNull}?.asString.orEmpty(),seconds("last_check"),seconds("next_check"),
            o.get("air_date")?.takeUnless{it.isJsonNull}?.asString)
    }.getOrNull()
}.sortedWith(compareBy<WatchEpisode>{it.season}.thenBy{it.number})

/** Compare saved intent to the canonical service rule, never to its id alone. */
internal fun watchRuleMatches(rule:WatchRuleModel,remote:JsonObject):Boolean {
    val expected=WatcherCodec.payloadFor(rule,true)
    return expected.entrySet().filterNot{it.key in setOf("confirm","confirmed")}.all{(key,value)->
        val other=remote.get(key)
        when {
            key=="series" -> value.asJsonObject.entrySet().all{(id,v)->
                val actual=other?.takeIf{it.isJsonObject}?.asJsonObject?.get(id)
                if(id in setOf("tmdb","tvdb"))actual?.isJsonPrimitive==true&&actual.asString==v.asString
                else actual==v
            }
            // The Python rule parser canonicalizes strict 4K to wait=0 and
            // a legacy zero wait in other modes to its default of 24 hours.
            // These representations are the same acquisition intent.
            key=="resolutionWaitHours" -> other?.isJsonPrimitive==true&&other.asInt==watchCanonicalWait(rule)
            // Compare domain values, not JsonPrimitive hash sets: Gson's parsed
            // numeric representation can hash differently from locally built ints.
            key=="watchedSeasons" -> other?.isJsonArray==true&&value.asJsonArray.map{it.asInt}.toSet()==other.asJsonArray.map{it.asInt}.toSet()
            key=="releaseTypes" -> other?.isJsonArray==true&&value.asJsonArray.map{it.asString}.toSet()==other.asJsonArray.map{it.asString}.toSet()
            else -> value==other
        }
    }
}

internal fun watchCanonicalWait(rule:WatchRuleModel):Int =
    if(rule.resolutionMode==WatchModes.RESOLUTION_2160_ONLY)0
    else rule.resolutionWaitHours.takeUnless{it==0}?:24

fun autoAddMode(rule:WatchRuleModel?):String=when {
    rule==null->"Off"
    rule.pendingAction=="off"->"Stop pending"
    rule.state!=WatchRuleModel.STATE_SERVER_CONFIRMED->"Pending sync"
    rule.paused->"Paused"
    else->"Active"
}

fun watchQuality(rule:WatchRuleModel):String=buildList {
    add(when(rule.resolutionMode){WatchModes.RESOLUTION_2160_ONLY->"4K only";WatchModes.RESOLUTION_1080_ONLY->"1080p";else->"4K → 1080p"})
    addAll(rule.releaseTypes)
    if(rule.hdrMode!=WatchModes.MODE_ANY)add(if(rule.hdrMode==WatchModes.MODE_MANDATORY)"HDR required" else "HDR preferred")
    if(rule.dvMode!=WatchModes.MODE_ANY)add(if(rule.dvMode==WatchModes.MODE_MANDATORY)"DV required" else "DV preferred")
    if(rule.atmosMode!=WatchModes.MODE_ANY)add(if(rule.atmosMode==WatchModes.MODE_MANDATORY)"Atmos required" else "Atmos preferred")
}.joinToString(" · ")

fun watchStatusLabel(status:String):String=when(status){
    "waiting_release"->"Waiting for release"
    "waiting_release_match"->"Waiting for a matching release"
    "waiting_torbox_cache"->"Waiting for TorBox cache"
    "added_torbox"->"Added to TorBox"
    "waiting_library"->"Waiting for Jellyfin"
    "available_jellyfin"->"Available in Jellyfin"
    "connection_unavailable"->"Connection needs attention"
    "paused"->"Paused"
    else->"Waiting for an update"
}

fun watchEpisodeReason(row:WatchEpisode):String {
    val text=row.detail.lowercase()
    return when {
        row.status=="connection_unavailable"->"A connected service could not be reached. Your rule remains saved."
        row.status=="waiting_torbox_cache"->"A release must be cached before it can be added."
        row.status=="available_jellyfin"->"This episode has been verified in your Jellyfin library."
        row.status=="paused"->"No new releases will be added while paused."
        "resolution" in text||"2160" in text||"1080" in text->"Available releases do not meet your resolution setting."
        "atmos" in text->"No release with the required Atmos audio has been verified."
        "hdr" in text||"vision" in text->"No release with the required HDR format has been verified."
        "language" in text||"hungarian" in text->"The required audio language could not be verified."
        "size" in text->"Available releases do not meet your episode size limit."
        "season not watched" in text->"Outside your selected episodes or seasons."
        row.status=="waiting_release"->row.airDate?.let{"Air date: $it. Only eligible released episodes are searched."}?:"The episode air date is not known yet."
        row.status=="waiting_library"->"Waiting for publication or indexing in Jellyfin."
        row.status=="added_torbox"->"The release was added; library publication is next."
        else->"No release matching your settings has been verified yet."
    }
}

fun watchFeatured(rows:List<WatchEpisode>):WatchEpisode?=rows.minWithOrNull(compareBy<WatchEpisode>{
    when(it.status){"connection_unavailable"->0;"added_torbox","waiting_library"->1;"waiting_torbox_cache"->2;"waiting_release_match"->3;"waiting_release"->4;"available_jellyfin"->5;else->6}
}.thenBy{if(it.status=="available_jellyfin")-it.season else it.season}.thenBy{if(it.status=="available_jellyfin")-it.number else it.number})

fun watchNeedsAttention(rule:WatchRuleModel,status:WatchRuleStatus?):Boolean = rule.state!=WatchRuleModel.STATE_SERVER_CONFIRMED||
    rule.pendingAction!=null||(status?.statuses?.get("connection_unavailable")?:0)>0

fun watchInProgress(rule:WatchRuleModel,status:WatchRuleStatus?):Boolean = !rule.paused&&rule.pendingAction!="off"&&
    status?.statuses?.any{it.value>0&&it.key in setOf("waiting_release_match","waiting_torbox_cache","added_torbox","waiting_library")}==true

fun watchRelative(at:Long,now:Long=System.currentTimeMillis()):String=when {
    at<=0->"Not checked yet"
    at>now->"in ${((at-now)/60000).coerceAtLeast(1)} min"
    now-at<60000->"Just now"
    now-at<3600000->"${(now-at)/60000} min ago"
    now-at<86400000->"${(now-at)/3600000} h ago"
    else->"${(now-at)/86400000} d ago"
}
