package app.stak.nativeapp.data

import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only the explicit IMDb field on an exactly matching Cinemeta identity is accepted. */
class ImdbRating(private val request:(String,Map<String,String>,String?)->JsonObject=::providerJson){
    suspend fun get(type:String,id:String):Double?=withContext(Dispatchers.IO){
        require(type in listOf("movie","series")&&id.matches(Regex("tt\\d+")))
        val meta=request("https://v3-cinemeta.strem.io/meta/$type/$id.json",emptyMap(),null).objectOrNull("meta")?:return@withContext null
        if((meta.text("imdb_id")?:meta.text("id"))!=id)return@withContext null
        meta.number("imdbRating")?.takeIf{it.isFinite()&&it>0&&it<=10}
    }
}
