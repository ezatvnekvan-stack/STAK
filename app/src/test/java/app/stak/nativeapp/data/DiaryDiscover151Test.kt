package app.stak.nativeapp.data
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import com.google.gson.JsonParser
import app.stak.nativeapp.ui.screens.*
class DiaryDiscover151Test{
 private val today=LocalDate.of(2026,11,10)
 private fun row(id:String,date:LocalDate?,type:String="movie",episode:Int?=null)=JournalEntry(MediaItem(id,name=id,type=type,season=if(episode!=null)1 else null,episode=episode),date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),source="Jellyfin")
 @Test fun monthSelectionExcludesUndatedFutureAndOtherMonths(){
  val rows=listOf(row("now",today),row("prior",today.minusMonths(1)),row("unknown",null),row("future",today.plusDays(1)))
  assertEquals(listOf("now"),diaryEntries(rows,YearMonth.from(today),today).map{it.media.id})
 }
 @Test fun groupsArePerSeriesPerDayNotPerTitleName(){
  val a=row("a",today,"series",1);val b=a.copy(media=a.media.copy(episode=2));val c=row("b",today,"series",1).copy(media=row("b",today,"series",1).media.copy(name="a"))
  val groups=historyDays(diaryEntries(listOf(a,b,c,a.copy(watchedAt=a.watchedAt!!-86400000)),YearMonth.from(today),today)).first().groups
  assertEquals(2,groups.size);assertEquals(2,groups.first{it.first.media.id=="a"}.entries.size)
 }
 @Test fun monthPickerRetainsOlderDatedHistory(){assertTrue(diaryMonths(listOf(row("old",today.minusYears(3))),today).contains(YearMonth.from(today.minusYears(3))))}
 @Test fun discoveryShelfDuplicatesKeepOnlyFirstOccurrence(){
  val a=MediaItem("a",name="A");val b=MediaItem("b",name="B")
  val deck=distinctDiscoverDeck(DiscoverDeck(listOf(DiscoverShelf("top",listOf(a),false),DiscoverShelf("trending",listOf(a,b),false),DiscoverShelf("hidden",listOf(b),false))))
  assertEquals(listOf("a","b"),deck.shelves.flatMap{it.items}.map{it.id});assertFalse(deck.shelves.last().loading)
 }
 @Test fun selectedStreamingServiceMustActuallyBePresentInRegion(){
  val detail=JsonParser.parseString("""{"first_air_date":"2025-01-01"}""").asJsonObject
  val provider=JsonParser.parseString("""{"flatrate":[{"provider_id":8}],"rent":[{"provider_id":9}]}""").asJsonObject
  assertTrue(streamingConfirmed(detail,provider,"series","US",today,"8"));assertFalse(streamingConfirmed(detail,provider,"series","US",today,"9"));assertTrue(streamingConfirmed(detail,provider,"series","US",today,"8|9"))
 }
}
