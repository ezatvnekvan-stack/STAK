package app.stak.nativeapp
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import org.junit.Test
import org.junit.Assert.*
class CachedUpgradeFixtureTest {
 @Test fun seed(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val settings=SettingsStore(context);NativeRuntime.settings=settings
  SecureStorage(context).putSecretChecked("torbox_key","synthetic-cache-smoke137")
  settings.finishSetup();settings.reducedMotion=true
  StreamAddonStore(settings).save(listOf(StreamAddon("torrentio137","Torrentio","https://torrentio.strem.fun/manifest.json",false),StreamAddon("aio137","AIOStreams","https://aio.example.invalid/private/manifest.json",false)))
  TorznabStore(settings).save(listOf(TorznabIndexer("private137","TorrentLeech test","http://127.0.0.1:19787/1/api","synthetic-indexer-only",true)))
  assertTrue(context.getSharedPreferences("cached_upgrade137",0).edit().putString("playback",settings.playbackJson).commit())
 }
}
class CachedUpgradeVerifyTest {
 @Test fun credentialsAndTrackingSurvive(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext;val settings=SettingsStore(context);NativeRuntime.settings=settings
  assertEquals("synthetic-cache-smoke137",settings.torboxKey)
  assertEquals(listOf("Torrentio","AIOStreams"),StreamAddonStore(settings).all().map{it.name})
  assertEquals("synthetic-indexer-only",TorznabStore(settings).read().single().apiKey)
  assertEquals(context.getSharedPreferences("cached_upgrade137",0).getString("playback",null),settings.playbackJson)
 }
}
