package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.data.db.PreferenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Emulator-only fixture for the optimized connection path. */
class FilterUpgradeFixtureTest {
 @Test fun seedUnreadableLegacyData()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  NativeRuntime.settings=SettingsStore(context)
  val dao=DatabaseProvider.get(context).preferenceDao()
  assertTrue(context.getSharedPreferences("filter136_fixture",0).edit().putString("oldJournal",dao.get("media_intelligence_v1")).commit())
  dao.set(PreferenceEntity("media_intelligence_v1","{broken optional journal"))
 }
}
class FilterUpgradeVerifyTest {
 @Test fun optimizedSyncKeepsLegacyDataAndAcknowledgesApproval()=runBlocking{
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  NativeRuntime.settings=SettingsStore(context)
  val doc=ApprovalStore().read();val row=doc.entries.single{it.id=="13517"&&it.scope==torboxScope()}
  assertTrue(row.approved);assertEquals("processing",row.state);assertEquals(approvalConnectionId(doc.connection),row.sentTo)
  val dao=DatabaseProvider.get(context).preferenceDao();assertEquals("{broken optional journal",dao.get("media_intelligence_v1"))
  val old=context.getSharedPreferences("filter136_fixture",0).getString("oldJournal",null)
  if(old!=null)dao.set(PreferenceEntity("media_intelligence_v1",old))else dao.delete("media_intelligence_v1")
 }
}
