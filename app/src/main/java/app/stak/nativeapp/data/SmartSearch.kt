package app.stak.nativeapp.data

import java.text.Normalizer
import java.util.Locale

fun normalizedSearch(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
fun providerSearchQuery(value:String):String {
    val raw=value.trim().take(160)
    return Regex("(?i)(?:^|/)(tt[0-9]{5,12})(?:[/ ?#]|$)").find(raw)?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: raw
}
private fun oneEdit(a:String,b:String):Boolean {
    if(kotlin.math.abs(a.length-b.length)>1)return false
    var i=0;var j=0;var changes=0
    while(i<a.length&&j<b.length){if(a[i]==b[j]){i++;j++}else{if(++changes>1)return false;if(a.length>=b.length)i++;if(b.length>=a.length)j++}}
    return changes+(a.length-i)+(b.length-j)<=1
}
fun searchScore(item:MediaItem,query:String):Int {
    val q=normalizedSearch(providerSearchQuery(query));if(q.isBlank())return 0
    if(q==item.imdbId?.lowercase(Locale.ROOT)||q==item.id.lowercase(Locale.ROOT))return 1000
    if((listOf(item.name)+item.aliases.orEmpty()).any{normalizedSearch(it)==q})return 600
    val year=Regex("(?:^| )((?:19|20)[0-9]{2})$").find(q)?.groupValues?.get(1)
    if(year!=null&&item.year?.take(4)!=year)return 0
    val titleQuery=if(year!=null)q.removeSuffix(year).trim() else q
    if(titleQuery.isBlank())return 0
    return (listOf(item.name)+item.aliases.orEmpty()).maxOfOrNull{alias->
        val name=normalizedSearch(alias);val words=name.split(' ');val tokens=titleQuery.split(' ')
        when {
            name==titleQuery->500
            name.startsWith(titleQuery)->400
            name.contains(titleQuery)->300
            tokens.all{token->words.any{it.startsWith(token)}}->200
            tokens.all{token->words.any{it==token||token.length>=4&&oneEdit(it,token)}}->100
            else->0
        }
    } ?: 0
}
fun rankedSearch(items:List<MediaItem>,query:String,includeProviderMatches:Boolean=false):List<MediaItem> = items.withIndex().map{Triple(it.value,searchScore(it.value,query),it.index)}.filter{includeProviderMatches||it.second>0}.sortedWith(compareByDescending<Triple<MediaItem,Int,Int>>{it.second}.thenBy{it.third}).map{it.first}
