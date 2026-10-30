package app.stak.nativeapp.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Same stable title-first ranking as src/lib/searchRelevance.js. */
object SearchRanking {
    fun normalize(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
    private fun distance(left: String, right: String): Int {
        val a=left.take(100);val b=right.take(100)
        var row=IntArray(b.length+1){it}
        for(i in 1..a.length) {
            val next=IntArray(b.length+1);next[0]=i
            for(j in 1..b.length) next[j]=minOf(next[j-1]+1,row[j]+1,row[j-1]+if(a[i-1]==b[j-1])0 else 1)
            row=next
        }
        return row[b.length]
    }
    fun relevance(item: MediaItem, query: String): Double {
        val raw=query.trim()
        val year=Regex("\\b((?:19|20)\\d{2})\\)?$").find(raw)?.groupValues?.get(1)
        val stripped=raw.replace(Regex("\\(?\\b(?:19|20)\\d{2}\\)?$"),"").trim()
        val term=normalize(if(year!=null && stripped.isNotBlank())stripped else raw)
        if(term.isBlank())return 0.0
        if(normalize(item.mediaKey)==term)return -1.0
        val names=(listOf(item.name)+item.aliases).filter{it.isNotBlank()}.map{normalize(it)}
        if(normalize(raw) in names)return -.5
        val tokens=term.split(' ')
        val score=names.minOfOrNull { name -> when {
            name==term -> 0.0
            name.startsWith("$term ") -> 100.0
            tokens.all{it in name.split(' ')} -> 200.0+abs(name.length-term.length)
            name.contains(term) -> 300.0+name.length-term.length
            else -> 400.0+(1.0-tokens.count{token->name.split(' ').any{it.startsWith(token)}}.toDouble()/tokens.size)*100+distance(name,term).toDouble()/maxOf(name.length,term.length,1)*100
        }} ?: 1000.0
        val titleYear=Regex("\\d{4}").find(item.year ?: item.releaseInfo.orEmpty())?.value
        return score+if(year!=null && titleYear!=year)30 else 0
    }
    fun rank(items: List<MediaItem>, query: String): List<MediaItem> = items.withIndex().sortedWith(compareBy<IndexedValue<MediaItem>>{relevance(it.value,query)}.thenBy{it.index}).map{it.value}
}
