package app.stak.nativeapp.data

import org.junit.Assert.*

import org.junit.Test

import java.time.*

class Presentation15Test{

 private val today=LocalDate.of(2026,11,10)

 private val show=MediaItem("tt1234567",imdbId="tt1234567",type="series",name="Fixture")

 private fun rule()=WatchRuleModel("watch_imdb_tt1234567",seriesImdb=show.imdbId,seriesName=show.name,enabled=true,watchedSeasons=listOf(1))

 @Test fun onlyExplicitEnabledRulesFeedHome(){

  assertEquals(1,followingMedia(listOf(rule(),rule().copy(enabled=false),rule().copy(pendingAction="off"))).size)

  assertTrue(followingMedia(listOf(rule().copy(seriesImdb=null))).isEmpty())

  assertEquals(1,followingMedia(listOf(rule().copy(paused=true))).size)

 }

 @Test fun upcomingExcludesMoviesUnknownCoordinatesAndUnselectedSeasons(){

  val event=ReleaseEvent(show,today,"Ep",1,1)

  val feed=ReleaseFeed(listOf(event,event.copy(media=show.copy(type="movie")),event.copy(season=2),event.copy(episode=null),event.copy(date=today.minusDays(1))),emptyList(),1)

  assertEquals(listOf(event),followingUpcomingEvents(feed,listOf(rule()),today))

 }

 @Test fun upcomingKeepsSeparateFutureEpisodesSorted(){

  val events=listOf(ReleaseEvent(show,today.plusDays(4),"Two",1,2),ReleaseEvent(show,today,"One",1,1))

  assertEquals(listOf(1,2),followingUpcomingEvents(ReleaseFeed(events,emptyList(),1),listOf(rule()),today).map{it.episode})

 }

 @Test fun tmdbRulesKeepTheirOwnSeasonSelection(){
  val a=rule().copy(seriesImdb=null,seriesTmdb="100",watchedSeasons=listOf(1))
  val b=rule().copy(seriesImdb=null,seriesTmdb="200",watchedSeasons=listOf(2))
  val event=ReleaseEvent(show.copy(id="tmdb:200",imdbId=null),today,"Wrong season",1,1)
  assertTrue(followingUpcomingEvents(ReleaseFeed(listOf(event),emptyList(),1),listOf(a,b),today).isEmpty())
 }
 @Test fun badgesNeverInventAudioOrHdr(){

  val source=SourceRelease("Unknown")

  assertFalse(releaseBadges(source).contains("Atmos"));assertFalse(releaseBadges(source).contains("HDR"))

  assertTrue(releaseBadges(source.copy(hdr=true,dolbyVision=true,atmos=true,webdl=true)).containsAll(listOf("HDR","Dolby Vision","Atmos","WEB-DL")))

 }

 private fun entry(date:LocalDate?,played:Boolean=true)=JournalEntry(show.copy(type="movie"),date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),played,source="Jellyfin")

 @Test fun heatmapHasTwelveAlignedWeeks(){val days=activityDays(emptyList(),today);assertEquals(84,days.size);assertEquals(DayOfWeek.MONDAY,days.first().date.dayOfWeek);assertEquals(DayOfWeek.SUNDAY,days.last().date.dayOfWeek)}

 @Test fun heatmapDoesNotInventUndatedFutureOrUnwatchedActivity(){val days=activityDays(listOf(entry(today),entry(null),entry(today.plusDays(1)),entry(today.minusDays(1),false)),today);assertEquals(1,days.sumOf{it.count});assertEquals(1,days.first{it.date==today}.count)}

 @Test fun priorWindowDoesNotIncludeCurrentActivity(){assertEquals(0,activityDays(listOf(entry(today)),today,today.minusWeeks(12)).sumOf{it.count})}

 @Test fun unconfirmedRulesNeverClaimSuccessfulServerSync(){assertEquals("Saved · waiting for sync",followingSummary(rule(),null,emptyList()))}

}
