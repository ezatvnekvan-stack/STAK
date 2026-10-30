package app.stak.nativeapp

import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.*

class DeliveryPipelineTest {
    private var old:SettingsStore?=null;private lateinit var settings:SettingsStore;private lateinit var prefix:String
    @Before fun setup(){old=runCatching{NativeRuntime.settings}.getOrNull();prefix="delivery-${java.util.UUID.randomUUID()}-";settings=SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext,prefix);NativeRuntime.settings=settings;settings.torboxKey="synthetic-only";settings.jellyfinUrl="http://127.0.0.1:1";settings.jellyfinToken="synthetic-only";settings.jellyfinUserId="me";settings.zurgConnectionJson=Gson().toJson(ZurgConnection("https://dav.example/dav/",jellyfinAccount=jellyfinScope(),mountPath="/media"))}
    @After fun cleanup(){old?.let{NativeRuntime.settings=it};listOf("stak_settings","stak_secrets").forEach{InstrumentationRegistry.getInstrumentation().targetContext.deleteSharedPreferences(prefix+it)}}
    private class Fake:DeliveryGateway {
        val files=listOf(TorBoxFile("1","e1.mkv",100),TorBoxFile("2","e2.mkv",200))
        var ready=false;var visible=0;var indexed=0;var scans=0;var permitScan=true;val parents=mutableListOf<String?>()
        override suspend fun torrents(key:String)=listOf(TorBoxTorrent("123","Season",300,if(ready)1f else .5f,ready,"cached",files))
        override suspend fun list(config:ZurgConnection,path:String?)=DavListing(files.take(visible).map{DavEntry(config.endpoint+it.name,it.name,it.name,false,it.size)})
        override suspend fun items(params:Map<String,String>):JsonObject {parents+=params["ParentId"];if(params["IncludeItemTypes"]=="Series")return JsonParser.parseString("""{"Items":[{"Id":"series"}],"TotalRecordCount":1}""").asJsonObject;return JsonObject().apply{add("Items",JsonArray().apply{files.take(indexed).forEach{file->add(JsonObject().apply{addProperty("Id",file.id);add("MediaSources",JsonArray().apply{add(JsonObject().apply{addProperty("Path","/media/"+file.name);addProperty("Size",file.size)})})})}});addProperty("TotalRecordCount",indexed)}}
        override suspend fun scan(){scans++;require(permitScan)}
    }
    private suspend fun record():DeliveryRecord {val store=IntelligenceStore.get(settings);store.added("123",torboxScope(),jellyfinScope(),MediaItem("series",imdbId="tt1234567",type="series",name="Series"));return store.read().deliveries.single()}
    @Test fun packCannotBeFinishedFromOneFileOrPreExistingSeriesMetadata()=runBlocking {
        val gateway=Fake();var record=record()
        record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("processing",record.phase)
        gateway.ready=true;record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("torbox",record.phase)
        gateway.visible=1;record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("torbox",record.phase)
        gateway.visible=2;record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("awaiting",record.phase)
        gateway.indexed=1;record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("awaiting",record.phase)
        gateway.indexed=2;record=DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals("jellyfin",record.phase)
        Assert.assertTrue(gateway.parents.contains("series"));Assert.assertEquals(0,gateway.scans)
        Assert.assertEquals(1,IntelligenceStore.get(settings).read().events.count{it.kind=="Jellyfin indexed"})
        DeliveryTracking.check(record,gateway=gateway);Assert.assertEquals(1,IntelligenceStore.get(settings).read().events.count{it.kind=="Jellyfin indexed"});Unit
    }
    @Test fun deniedScanDoesNotEraseConfirmedWebDavVisibility()=runBlocking{val gateway=Fake().apply{ready=true;visible=2;permitScan=false};val result=DeliveryTracking.check(record(),true,gateway);Assert.assertEquals("awaiting",result.phase);Assert.assertTrue(result.note.contains("not confirmed"));Assert.assertNotNull(result.zurgPath);Assert.assertNull(result.scanRequestedAt);Unit}
    @Test fun scanAcceptanceIsNotIndexingSuccess()=runBlocking{val gateway=Fake().apply{ready=true;visible=2};val result=DeliveryTracking.check(record(),true,gateway);Assert.assertEquals("scan",result.phase);Assert.assertNotNull(result.scanRequestedAt);Assert.assertNull(result.jellyfinId);Unit}
    @Test fun switchingTorBoxAccountCannotCheckOldRecords()=runBlocking{val record=record();settings.torboxKey="another-account";try{DeliveryTracking.check(record,gateway=Fake());Assert.fail()}catch(_:IllegalArgumentException){};Unit}
}
