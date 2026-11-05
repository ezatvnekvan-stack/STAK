package app.stak.nativeapp.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/**
 * Offline tests for the series-only Auto-add rules: payload shape (mirrors the
 * watcher's rule validation), consent gate, saved-local vs server-confirmed
 * states, pause/resume/delete paths and the codec guards. Never touches a live
 * watcher or TorBox.
 */
class WatchRulesTest {

    private class FakePersistence(start:WatcherDocument=WatcherDocument()):WatchRulePersistence{
        var doc:WatcherDocument=start
        override fun read():WatcherDocument=doc
        override fun update(change:(WatcherDocument)->WatcherDocument):WatcherDocument{
            doc=change(doc);return doc
        }
    }

    /** Records every watcher API call; `responder` plays the watcher side. */
    private class FakeWatcher(private val responder:(String)->JsonObject):HttpWatcherTransport(){
        val paths=mutableListOf<String>();val methods=mutableListOf<String>();val bodies=mutableListOf<String>()
        val calls get()=paths.size
        override fun call(connection:WatcherConnection,path:String,body:JsonObject?,method:String):JsonObject{
            paths+=path;methods+=method;bodies+=body?.toString()?:""
            return responder(path)
        }
    }

    private fun fakeClientFactory(transport:FakeWatcher):(WatcherConnection)->WatcherClient={conn->WatcherClient(conn,transport)}
    private fun pairedPersistence():FakePersistence=FakePersistence(WatcherDocument(
        connection=WatcherConnection(url="https://watch.example/",token="watcher-token-1234567890",enabled=true)))

    private fun rule(ruleId:String=""):WatchRuleModel=WatchRuleModel(
        ruleId=ruleId.ifBlank{watchRuleIdFor("imdb:tt0111161")},
        seriesImdb="tt0111161",seriesName="Sample Series",enabled=true,
        watchedSeasons=listOf(1,2),releaseTypes=listOf(WatchModes.RELEASE_WEBDL,WatchModes.RELEASE_BLURAY),
        maxEpisodeGb=12.5)

    // --- rule id stability ---------------------------------------------------

    @Test fun ruleIdIsStableAndPrefixMatchesKeyType(){
        val imdb=watchRuleIdFor("imdb:tt0111161")
        assertEquals(imdb,watchRuleIdFor("imdb:tt0111161"))
        assertTrue(imdb.startsWith("watch_imdb_tt0111161_"))
        assertTrue(watchRuleIdFor("tmdb:424").startsWith("watch_tmdb_424_"))
        assertTrue(watchRuleIdFor("jellyfin:abc").startsWith("watch_jf_abc_"))
        assertNotEquals(imdb,watchRuleIdFor("imdb:tt0111162"))
        try{watchRuleIdFor("jellyfin:");fail("Empty id must be rejected")}catch(_:IllegalArgumentException){}
    }

    // --- payload shape: must match the watcher's POST /v1/rules validation ----

    @Test fun payloadCarriesWatcherRuleKeysAndExplicitConsent(){
        val payload=WatcherCodec.payloadFor(rule(),consent=true)
        val expected=setOf("ruleId","series","enabled","paused","createdOn","watchedSeasons",
            "includeSpecials","onlyNewEpisodes","resolutionMode","resolutionWaitHours","releaseTypes",
            "hdrMode","dvMode","atmosMode","languageMode","maxEpisodeGb","checkMinutes","confirm","confirmed")
        assertEquals(expected,payload.keySet())
        val series=payload.getAsJsonObject("series")
        assertEquals(setOf("imdb","name"),series.keySet())
        assertEquals("tt0111161",series.get("imdb").asString)
        assertEquals("Sample Series",series.get("name").asString)
        assertTrue(payload.get("confirm").asBoolean)
        assertTrue(payload.get("confirmed").asBoolean)
        assertEquals("WEB-DL",payload.getAsJsonArray("releaseTypes").get(0).asString)
        assertEquals(12.5,payload.get("maxEpisodeGb").asDouble,0.0)
    }

    @Test fun watchlistingAloneMustNotEnableAutomation(){
        try{WatcherCodec.payloadFor(rule(),consent=false);fail("Consent gate must refuse")}catch(_:IllegalArgumentException){}
    }

    // --- save flow: saved_local -> server_confirmed --------------------------

    @Test fun saveRuleStoresLocallyThenConfirmsThroughWatcherPost()=runBlocking{
        val persistence=pairedPersistence();val watcher=FakeWatcher{path->
            if(path=="v1/rules")JsonParser.parseString("""{"rule":{"ruleId":"${watchRuleIdFor("imdb:tt0111161")}"},"issues":[]}""").asJsonObject
            else JsonObject()
        }
        val result=WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=fakeClientFactory(watcher))
        // POST /v1/rules with the consent payload
        assertEquals("v1/rules",watcher.paths.single())
        assertEquals("POST",watcher.methods.single())
        assertTrue(watcher.bodies.single().contains("\"confirm\":true"))
        assertTrue(watcher.bodies.single().contains("\"confirmed\":true"))
        assertTrue(watcher.bodies.single().contains("\"ruleId\":\"${watchRuleIdFor("imdb:tt0111161")}\""))
        assertTrue(result.confirmed)
        assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,result.rule.state)
        assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,persistence.doc.rules.single().state)
    }

    @Test fun saveWithoutConsentNeverCallsTheWatcher()=runBlocking{
        val persistence=pairedPersistence();val watcher=FakeWatcher{fail("Watcher must not be called without consent");JsonObject()}
        try{
            WatchRules.saveRule(rule(),consent=false,persistence=persistence,clientFactory=fakeClientFactory(watcher))
            fail("Consent gate must refuse")
        }catch(_:IllegalArgumentException){}
        assertEquals(0,watcher.calls)
        // The rule itself still stays saved locally, disabled until consent.
        assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,persistence.doc.rules.single().state)
    }

    @Test fun saveWithoutPairingStaysSavedLocal()=runBlocking{
        val persistence=FakePersistence()
        val result=WatchRules.saveRule(rule(),consent=true,persistence=persistence,
            clientFactory={_->fail("No watcher is paired; nothing may call out");WatcherClient(WatcherConnection.NONE)})
        assertFalse(result.confirmed)
        assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,result.rule.state)
    }

    // --- off / paused ---------------------------------------------------------

    @Test fun turnOffDeletesRuleOnWatcherAndKeepsHistoryThere()=runBlocking{
        val key="imdb:tt0111161";val id=watchRuleIdFor(key)
        val persistence=pairedPersistence()
        persistence.doc=persistence.doc.copy(rules=listOf(rule(id)))
        val watcher=FakeWatcher{JsonObject()}
        WatchRules.turnOff(key,persistence=persistence,clientFactory=fakeClientFactory(watcher))
        assertEquals("v1/rules/$id",watcher.paths.single())
        assertEquals("DELETE",watcher.methods.single())
        assertTrue(persistence.doc.rules.isEmpty());assertTrue(persistence.doc.statuses.isEmpty())
    }

    @Test fun pauseAndResumeUseTheirEndpointPaths()=runBlocking{
        val key="imdb:tt0111161";val id=watchRuleIdFor(key)
        val persistence=pairedPersistence()
        persistence.doc=persistence.doc.copy(rules=listOf(rule(id)))
        val watcher=FakeWatcher{JsonObject()}
        WatchRules.setPaused(key,true,persistence=persistence,clientFactory=fakeClientFactory(watcher))
        assertEquals("v1/rules/$id/pause",watcher.paths.single());assertEquals("POST",watcher.methods.single())
        assertEquals(true,persistence.doc.rules.single().paused)
        WatchRules.setPaused(key,false,persistence=persistence,clientFactory=fakeClientFactory(watcher))
        assertEquals("v1/rules/$id/resume",watcher.paths[1])
        assertEquals(false,persistence.doc.rules.single().paused)
        // Both transitions confirm the rule with the watcher.
        assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,persistence.doc.rules.single().state)
    }

    // --- status refresh -------------------------------------------------------

    @Test fun refreshReadsRuleAndEpisodeRowsIntoStatusCache()=runBlocking{
        val key="imdb:tt0111161";val id=watchRuleIdFor(key)
        val persistence=pairedPersistence()
        persistence.doc=persistence.doc.copy(rules=listOf(rule(id)))
        val watcher=FakeWatcher{path->
            when{
                path=="v1/rules/$id"->JsonParser.parseString("""{"rule":{"ruleId":"$id","paused":true}}""").asJsonObject
                path.startsWith("v1/episodes")&&path.endsWith("rule_id=$id")->
                    JsonParser.parseString("""{"items":[{"status":"wanted"},{"status":"added"},{"status":"wanted"}]}""").asJsonObject
                else->run{fail("Unexpected watcher path $path");JsonObject()}
            }
        }
        val doc=WatchRules.refresh(persistence=persistence,clientFactory=fakeClientFactory(watcher))
        assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,doc.rules.single().state)
        val status=doc.statuses.single()
        assertEquals(id,status.ruleId)
        assertEquals(3,status.episodes)
        assertEquals(2,status.statuses["wanted"]);assertEquals(1,status.statuses["added"])
        assertTrue(status.paused);assertTrue(status.confirmed)
    }

    @Test fun statusReaderCountsRowsPurely(){
        val r=rule()
        val record=JsonParser.parseString("""{"rule":{"ruleId":"${r.ruleId}","paused":false}}""").asJsonObject.getAsJsonObject("rule")
        val rows=JsonParser.parseString("""[{"status":"wanted"},{"status":"wanted"},{"other":1}]""").asJsonArray
        val status=watchStatusFromRows(r,record,rows)
        assertEquals(2,status.episodes);assertEquals(2,status.statuses["wanted"])
    }

    // --- codec + storage guards ----------------------------------------------

    @Test fun codecRoundTripPreservesDocument(){
        val document=WatcherDocument(
            connection=WatcherConnection(url="https://watch.example/",token="watcher-token-1234567890",enabled=true),
            rules=listOf(rule()),statuses=listOf(WatchRuleStatus(rule().ruleId,"Sample Series",true,true,7,
                mapOf("wanted" to 5,"added" to 2),123L)))
        val decoded=WatcherCodec.decode(WatcherCodec.encode(document))
        assertEquals(document,decoded)
    }

    @Test fun decodeBlankIsAnEmptyDocumentAndGarbageIsRefusedNotOverwritten(){
        assertEquals(WatcherDocument(),WatcherCodec.decode(null))
        assertEquals(WatcherDocument(),WatcherCodec.decode(""))
        for(garbage in listOf("{","[]","{\"version\":2}")){
            try{WatcherCodec.decode(garbage);fail("Must refuse $garbage")}
            catch(error:IOException){assertTrue(error.message!!.startsWith("Saved watcher rules could not be read"))}
        }
    }

    // --- endpoint + token guards ----------------------------------------------

    @Test fun endpointAllowsHttpsAndPrivateLanOnly(){
        assertEquals("https://watch.example/",watchEndpoint("https://watch.example").toString())
        assertEquals("http://192.168.1.5:8096/",watchEndpoint("http://192.168.1.5:8096").toString())
        assertEquals("http://localhost/",watchEndpoint("http://localhost").toString())
        for(bad in listOf("http://watch.example","ftp://192.168.0.1",
            "https://user:pass@watch.example/","https://watch.example/?x=1")){
            try{watchEndpoint(bad);fail("Must refuse $bad")}catch(_:WatcherFailure){}
        }
    }

    @Test fun tokenGuardRejectsShortAndBrokenValues(){
        assertEquals("clean-token-1234",watchToken("  clean-token-1234  "))
        for(bad in listOf("short","line\r\nbreak")){
            try{watchToken(bad);fail("Must refuse")}catch(_:WatcherTokenError){}
        }
    }

    // --- cloud backup wiring ---------------------------------------------------

    @Test fun statusCacheIsExcludedFromBackupWhileRulesJoinIt(){
        // Transient server state is re-fetched, so it must not travel in backups.
        assertFalse(cloudBackupKey("watch_status_cache"))
        // Ordinary setting keys still travel (rules live in secure storage secrets).
        assertTrue(cloudBackupKey("watch_rules_pane"))
    }
}
