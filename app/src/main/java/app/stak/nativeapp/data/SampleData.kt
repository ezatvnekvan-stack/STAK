package app.stak.nativeapp.data

/**
 * Offline/fallback catalogue.
 *
 * Artwork comes from Stremio's public MetaHub image service, which resolves
 * posters and backdrops from an IMDb id. It keeps the native app visually
 * identical with or without a reachable STAK backend.
 */
object SampleData {
    private fun posterFor(id: String) = "https://images.metahub.space/poster/medium/$id/img"
    private fun backdropFor(id: String) = "https://images.metahub.space/background/medium/$id/img"

    private fun item(
        id: String,
        type: String,
        name: String,
        year: String,
        runtime: String,
        rating: Double,
        tmdb: Double,
        genres: List<String>,
        overview: String,
        badge: String? = null
    ) = MediaItem(
        id = id,
        imdbId = id,
        type = type,
        name = name,
        year = year,
        runtime = runtime,
        rating = rating,
        tmdbRating = tmdb,
        genres = genres,
        overview = overview,
        poster = posterFor(id),
        background = backdropFor(id),
        badge = badge
    )

    val catalog: List<MediaItem> = listOf(
        item(
            "tt15239678", "movie", "Dune: Part Two", "2024", "2h 47m", 8.5, 8.2,
            listOf("Action", "Adventure", "Drama"),
            "Paul Atreides unites with the Fremen while on a warpath of revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the universe, he must prevent a terrible future only he can foresee.",
            "4K"
        ),
        item(
            "tt15398776", "movie", "Oppenheimer", "2023", "3h 1m", 8.3, 8.1,
            listOf("Drama", "History"),
            "The story of J. Robert Oppenheimer's role in the development of the atomic bomb during World War II.",
            "REMUX"
        ),
        item(
            "tt14452776", "series", "The Bear", "2022", "30m", 8.7, 8.4,
            listOf("Drama", "Comedy"),
            "A young chef from the fine dining world returns to Chicago to run his family's sandwich shop.",
            "NEW"
        ),
        item(
            "tt11280740", "series", "Severance", "2022", "50m", 8.7, 8.5,
            listOf("Drama", "Mystery"),
            "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives. When a mysterious colleague appears outside of work, it begins a journey to discover the truth about their jobs.",
            "ATMOS"
        ),
        item(
            "tt12637874", "series", "Shogun", "2024", "60m", 8.6, 8.6,
            listOf("Drama", "War"),
            "In Japan in the year 1600, at the dawn of a century-defining civil war, Lord Yoshii Toranaga fights for his life as his enemies unite against him.",
            "4K"
        ),
        item(
            "tt0816692", "movie", "Interstellar", "2014", "2h 49m", 8.7, 8.4,
            listOf("Science Fiction", "Drama"),
            "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
            "HDR"
        ),
        item(
            "tt1877830", "movie", "The Batman", "2022", "2h 56m", 7.8, 7.7,
            listOf("Crime", "Mystery"),
            "When a sadistic serial killer begins murdering key political figures in Gotham, Batman is forced to investigate the city's hidden corruption.",
            "DV"
        ),
        item(
            "tt1856101", "movie", "Blade Runner 2049", "2017", "2h 44m", 8.0, 7.6,
            listOf("Science Fiction", "Drama"),
            "Young Blade Runner K's discovery of a long-buried secret leads him to track down former Blade Runner Rick Deckard.",
            "4K"
        ),
        item(
            "tt3581920", "series", "The Last of Us", "2023", "55m", 8.7, 8.6,
            listOf("Drama", "Action"),
            "Twenty years after modern civilization has been destroyed, Joel and Ellie are hired to smuggle a girl out of an oppressive quarantine zone.",
            "HDR"
        ),
        item(
            "tt13622970", "series", "Andor", "2022", "45m", 8.4, 8.1,
            listOf("Drama", "Sci-Fi"),
            "The tale of the burgeoning rebellion against the Empire and how people and planets became involved.",
            "ATMOS"
        ),
        item(
            "tt11198330", "series", "House of the Dragon", "2022", "60m", 8.4, 8.4,
            listOf("Drama", "Fantasy"),
            "An internal succession war within House Targaryen at the height of its power, 172 years before the birth of Daenerys Targaryen.",
            "4K"
        ),
        item(
            "tt7660850", "series", "Succession", "2018", "60m", 8.9, 8.3,
            listOf("Drama", "Comedy"),
            "The Roy family is known for controlling the biggest media and entertainment company in the world. However, their world changes when their father steps down.",
            "WEB-DL"
        ),
        item(
            "tt1520211", "series", "Fallout", "2024", "55m", 8.4, 8.3,
            listOf("Sci-Fi", "Action"),
            "In a future post-apocalyptic Los Angeles, a vault dweller ventures to the surface and discovers a world of survivors.",
            "NEW"
        ),
        item(
            "tt13238346", "movie", "Past Lives", "2023", "1h 46m", 7.8, 7.8,
            listOf("Romance", "Drama"),
            "Nora and Hae Sung, two deeply connected childhood friends, are wrest apart after Nora's family emigrates from South Korea.",
            "1080p"
        ),
        item(
            "tt14230458", "movie", "Poor Things", "2023", "2h 21m", 7.7, 7.9,
            listOf("Science Fiction", "Romance"),
            "Brought back to life by an unorthodox scientist, a young woman runs off on a whirlwind adventure across the continents.",
            "DV"
        ),
        item(
            "tt6710474", "movie", "Everything Everywhere All at Once", "2022", "2h 19m", 7.8, 7.8,
            listOf("Action", "Comedy"),
            "An aging Chinese immigrant is swept up in an insane adventure where she alone can save the world by exploring other universes.",
            "4K"
        ),
        item(
            "tt9362722", "movie", "Spider-Man: Across the Spider-Verse", "2023", "2h 20m", 8.5, 8.4,
            listOf("Animation", "Action"),
            "Miles Morales catapults across the multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
            "HDR"
        ),
        item(
            "tt12037194", "movie", "Furiosa: A Mad Max Saga", "2024", "2h 28m", 7.5, 7.6,
            listOf("Action", "Adventure"),
            "As the world falls, young Furiosa is snatched from the Green Place of Many Mothers and falls into the hands of a great biker horde.",
            "REMUX"
        ),
        item(
            "tt16426418", "movie", "Challengers", "2024", "2h 11m", 7.1, 7.2,
            listOf("Drama", "Romance"),
            "Tennis player turned coach Tashi has taken her husband, Art, and transformed him into a world-famous grand slam champion.",
            "1080p"
        ),
        item(
            "tt29623480", "movie", "The Wild Robot", "2024", "1h 42m", 8.2, 8.5,
            listOf("Animation", "Family"),
            "After a shipwreck, an intelligent robot called Roz is stranded on an uninhabited island.",
            "4K"
        )
    )

    /** Matches the eight saved titles in the shipped watchlist reference. */
    val seedWatchlist: List<MediaItem> = listOf(
        "tt15239678" to 0.72f,
        "tt15398776" to 0.54f,
        "tt14452776" to 0.40f,
        "tt11280740" to 0f,
        "tt12637874" to 0f,
        "tt0816692" to 0f,
        "tt1877830" to 0f,
        "tt3581920" to 0f
    ).mapNotNull { (id, progress) ->
        catalog.firstOrNull { it.imdbId == id }?.copy(progress = progress)
    }

    val continueWatching: List<MediaItem> = listOf(
        catalog.first { it.id == "tt11280740" }.copy(progress = 0.42f, releaseInfo = "S02E03 · 24 min left"),
        catalog.first { it.id == "tt12637874" }.copy(progress = 0.78f, releaseInfo = "S01E08 · 11 min left"),
        catalog.first { it.id == "tt14452776" }.copy(progress = 0.18f, releaseInfo = "S03E01 · 27 min left"),
        catalog.first { it.id == "tt3581920" }.copy(progress = 0.64f, releaseInfo = "S01E05 · 19 min left")
    )

    fun hero(): List<MediaItem> = listOf(
        catalog[0], catalog[1], catalog[3], catalog[4], catalog[5], catalog[8]
    )

    fun homeFeed(): HomeFeed = HomeFeed(
        hero = hero(),
        continueWatching = continueWatching,
        rows = homeRows(),
        source = "sample"
    )

    fun homeRows(): List<HomeRow> {
        // The web feed draws trending rows from live TMDb, so on a reference
        // build the trending posters do not overlap the seeded watchlist. Lead
        // each row with unsaved titles to mirror that on the offline fallback.
        val seedIds = seedWatchlist.map { it.imdbId }.toSet()
        val movies = catalog.filter { it.type == "movie" && it.imdbId !in seedIds } +
            catalog.filter { it.type == "movie" && it.imdbId in seedIds }
        val series = listOf(
            "tt11280740", // Severance
            "tt1520211",  // Fallout
            "tt11198330", // House of the Dragon
            "tt13622970", // Andor
            "tt7660850",  // Succession
            "tt12637874", // Shogun
            "tt14452776", // The Bear
            "tt3581920"   // The Last of Us
        ).mapNotNull { id -> catalog.firstOrNull { it.imdbId == id } }
        return listOf(
            HomeRow("trending_movie", "Trending movies", movies.take(10)),
            HomeRow("trending_series", "Trending series", series.take(10)),
            HomeRow("top_movie", "Top rated movies", movies.sortedByDescending { it.rating ?: 0.0 }),
            HomeRow("top_series", "Top rated series", series.sortedByDescending { it.rating ?: 0.0 }),
            HomeRow("new_movie", "Released in 2024 – movies", movies.sortedByDescending { it.year ?: "" }),
            HomeRow("new_series", "Released in 2024 – series", series.sortedByDescending { it.year ?: "" })
        )
    }

    fun episodes(series: MediaItem): List<Episode> {
        val names = listOf(
            "Pilot", "Half Loop", "In Perpetuity", "The You You Are",
            "The Grim Barbarity of Optics and Design", "Hide and Seek",
            "Defiant Jazz", "What's for Dinner?"
        )
        return names.mapIndexed { index, name ->
            Episode(
                season = 1,
                episode = index + 1,
                name = name,
                overview = "The team navigates another strange day at Lumon Industries while the boundaries between work and life blur.",
                image = backdropFor(series.id),
                runtime = "${38 + index}m",
                progress = if (index == 0) 1f else 0f,
                watched = index == 0
            )
        }
    }

    fun sources(item: MediaItem? = null): List<SourceRelease> {
        val name = item?.name ?: "Dune: Part Two"
        val year = item?.year ?: "2024"
        val slug = name.replace(Regex("[^A-Za-z0-9]+"), ".").trim('.') + "." + year
        return listOf(
            SourceRelease("$slug.2160p.UHD.BluRay.REMUX.DV.HDR.HEVC.TrueHD.7.1.Atmos", "2160p", "71.4 GB", 1240, true, true, true, true, false, "Torrentio"),
            SourceRelease("$slug.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265", "2160p", "24.8 GB", 864, true, true, true, false, true, "TorBox"),
            SourceRelease("$slug.1080p.BluRay.REMUX.AVC.DTS-HD.MA.7.1", "1080p", "32.1 GB", 512, true, false, false, true, false, "Torrentio"),
            SourceRelease("$slug.1080p.WEB-DL.DDP5.1.H.264", "1080p", "12.6 GB", 392, true, false, false, false, true, "TorBox"),
            SourceRelease("$slug.720p.WEB-DL.AAC2.0.H.264", "720p", "4.2 GB", 188, false, false, false, false, true, "Torrentio")
        )
    }

    fun jellyfin(): List<MediaItem> = listOf(
        catalog.first { it.id == "tt11280740" }.copy(progress = 0.42f, releaseInfo = "S02E03", jellyfinId = "jf-1"),
        catalog.first { it.id == "tt12637874" }.copy(progress = 0.78f, releaseInfo = "S01E08", jellyfinId = "jf-2"),
        catalog.first { it.id == "tt14452776" }.copy(progress = 0.18f, releaseInfo = "S03E01", jellyfinId = "jf-3"),
        catalog.first { it.id == "tt3581920" }.copy(progress = 0.9f, releaseInfo = "S01E05", jellyfinId = "jf-4"),
        catalog.first { it.id == "tt0816692" }.copy(releaseInfo = "Movie", jellyfinId = "jf-5"),
        catalog.first { it.id == "tt1856101" }.copy(releaseInfo = "Movie", jellyfinId = "jf-6"),
        catalog.first { it.id == "tt13622970" }.copy(releaseInfo = "S02E01", jellyfinId = "jf-7"),
        catalog.first { it.id == "tt11198330" }.copy(releaseInfo = "S02E04", jellyfinId = "jf-8")
    )

    fun tracking(): List<MediaItem> = listOf(
        catalog[0].copy(progress = 0.72f, releaseInfo = "2024"),
        catalog[1].copy(progress = 0.54f, releaseInfo = "2023"),
        catalog[2].copy(progress = 0.40f, releaseInfo = "S01 E02 · The Bear"),
        catalog[3].copy(progress = 0.40f, releaseInfo = "S01 E02 · Severance"),
        catalog[2].copy(progress = 1f, watched = true, releaseInfo = "Yesterday · 1 episode watched"),
        catalog[3].copy(progress = 1f, watched = true, releaseInfo = "Yesterday · 1 episode watched")
    )
}
