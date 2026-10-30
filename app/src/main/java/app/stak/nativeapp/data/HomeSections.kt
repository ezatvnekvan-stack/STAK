package app.stak.nativeapp.data

import com.google.gson.JsonParser

data class HomeSection(val id: String, val title: String, val type: String = "movie", val category: String = "trending", val enabled: Boolean = true, val year: Int? = null)

object HomeSections {
    fun defaults(): List<HomeSection> {
        val year = java.time.LocalDate.now().year
        return listOf(
            HomeSection("continue", "Continue watching"),
            HomeSection("coming_up", "Coming up"),
            HomeSection("watchlist", "Your watchlist"),
            HomeSection("trending_movie", "Trending movies"),
            HomeSection("trending_series", "Trending series", "series"),
            HomeSection("anticipated_movie", "Most anticipated movies", category="anticipated"),
            HomeSection("anticipated_series", "Most anticipated series", "series", "anticipated"),
            HomeSection("top_movie", "Top rated movies", category = "top"),
            HomeSection("new_movie", "Released in $year · movies", category = "new", year = year),
            HomeSection("top_series", "Top rated series", "series", "top", false),
            HomeSection("new_series", "Released in $year · series", "series", "new", false, year)
        )
    }

    fun read(json: String?): List<HomeSection> {
        val defaults = defaults()
        if (json.isNullOrBlank()) return defaults
        return try {
            val stored = JsonParser.parseString(json).asJsonArray.mapNotNull { entry ->
                val item = entry.asJsonObject
                defaults.find { it.id == item.text("id") }?.copy(enabled = item.get("on")?.asBoolean ?: true)
            }.distinctBy { it.id }
            val merged=stored.toMutableList()
            defaults.filter{d->stored.none{it.id==d.id}}.forEach{missing->
                val after=when(missing.id){"coming_up"->"continue";"anticipated_movie"->"trending_movie";"anticipated_series"->"trending_series";else->null}
                val index=after?.let{id->merged.indexOfFirst{it.id==id}} ?: -1
                if(index>=0)merged.add(index+1,missing)else merged.add(missing)
            }
            merged
        } catch (_: RuntimeException) { defaults }
    }
}
