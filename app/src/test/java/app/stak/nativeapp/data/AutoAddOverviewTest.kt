package app.stak.nativeapp.data

import com.google.gson.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AutoAddOverviewTest {
    private val id=watchRuleIdFor("imdb:tt1234567")
    private fun rule()=WatchRuleModel(id,seriesImdb="tt1234567",seriesName="Example",enabled=true,
        releaseTypes=listOf("WEB-DL"),state=WatchRuleModel.STATE_SERVER_CONFIRMED)
    private class Memory(rule:WatchRuleModel):WatchRulePersistence{
        var doc=WatcherDocument(connection=WatcherConnection("https://watch.example/","test-token-123456789",true),rules=listOf(rule))
        override fun read()=doc
        override fun update(change:(WatcherDocument)->WatcherDocument):WatcherDocument{doc=change(doc);return doc}
    }
    private fun client(reply:(String,String,JsonObject?)->JsonObject):(WatcherConnection)->WatcherClient={c->
        WatcherClient(c,object:HttpWatcherTransport(){override fun call(connection:WatcherConnection,path:String,body:JsonObject?,method:String)=reply(path,method,body)})
    }

    @Test fun centralListCanUseRuleIdsForPauseAndOff()=runBlocking{
        val p=Memory(rule());val methods=mutableListOf<String>()
        val factory=client{path,method,_->methods+="$method $path";JsonObject()}
        WatchRules.setPaused(id,true,p,factory)
        assertEquals("POST v1/rules/$id/pause",methods.single())
        assertEquals("Paused",autoAddMode(p.doc.rules.single()))
        WatchRules.turnOff(id,p,factory)
        assertEquals("DELETE v1/rules/$id",methods.last());assertTrue(p.doc.rules.isEmpty())
    }

    @Test fun offlineStopRetainsRuleUntilAcknowledgedAndRestoreNeverReplays()=runBlocking{
        val p=Memory(rule());var writes=0
        WatchRules.turnOff(id,p,client{_,_,_->writes++;throw IOException("offline")})
        assertEquals(1,writes);assertEquals("off",p.doc.rules.single().pendingAction)
        assertEquals("Stop pending",autoAddMode(p.doc.rules.single()))
        p.doc=WatcherCodec.decode(WatcherCodec.encode(p.doc))
        WatchRules.refresh(p,client{path,method,_->
            assertEquals("GET",method)
            if(path.contains("episodes"))JsonObject().apply{add("items",JsonArray())}
            else WatcherCodec.payloadFor(rule(),true)
        })
        assertEquals("off",p.doc.rules.single().pendingAction)
        assertEquals(1,writes)
    }

    @Test fun interruptedStopCanBeConfirmedByReadOnlyRefresh()=runBlocking{
        val p=Memory(rule().copy(pendingAction="off",state=WatchRuleModel.STATE_SAVED_LOCAL))
        WatchRules.refresh(p,client{path,method,_->
            assertEquals("GET",method)
            if(path.contains("episodes"))JsonObject().apply{add("items",JsonArray())}
            else WatcherCodec.payloadFor(rule().copy(paused=true),true)
        })
        assertTrue(p.doc.rules.isEmpty())
    }

    @Test fun offlineEditDoesNotKeepOldConfirmationOrConfirmByIdAlone()=runBlocking{
        val original=rule();val edited=original.copy(hdrMode=WatchModes.MODE_MANDATORY)
        val p=Memory(original)
        WatchRules.saveRule(edited,true,p,client{_,_,_->throw IOException("offline")})
        assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,p.doc.rules.single().state)
        WatchRules.refresh(p,client{path,_,_->if(path.contains("episodes"))JsonObject().apply{add("items",JsonArray())} else WatcherCodec.payloadFor(original,true)})
        assertEquals("save",p.doc.rules.single().pendingAction)
        assertEquals("Pending sync",autoAddMode(p.doc.rules.single()))
    }

    @Test fun matchingReadConfirmsEditWithoutResendingIt()=runBlocking{
        val r=rule().copy(pendingAction="save",state=WatchRuleModel.STATE_SAVED_LOCAL)
        val p=Memory(r)
        WatchRules.refresh(p,client{path,method,_->
            assertEquals("GET",method)
            if(path.contains("episodes"))JsonObject().apply{add("items",JsonArray())} else WatcherCodec.payloadFor(r,true)
        })
        assertEquals("Active",autoAddMode(p.doc.rules.single()))
        assertNull(p.doc.rules.single().pendingAction)
    }

    @Test fun probeChecksProtectedEndpointAndRejectsBadToken()=runBlocking{
        val paths=mutableListOf<String>()
        try{
            WatchRules.probe(WatcherConnection("https://watch.example/","test-token-123456789",true),client{path,_,_->
                paths+=path
                if(path=="health")JsonObject().apply{addProperty("ok",true);addProperty("version","1.1.0")}
                else throw WatcherFailure("The watcher rejected the token (HTTP 401).")
            })
            fail("Public health is not proof of pairing")
        }catch(_:WatcherFailure){}
        assertEquals(listOf("health","v1/status"),paths)
    }

    @Test fun qualityDefaultsAndPendingStateSurvivePortableBackup(){
        val profile=WatchQualityProfile(hdr=WatchModes.MODE_MANDATORY,atmos=WatchModes.MODE_PREFERRED)
        val r=rule().copy(pendingAction="off",poster="https://example.com/poster.jpg")
        val doc=WatcherDocument(rules=listOf(r),qualityDefault=profile)
        assertEquals(doc,WatcherCodec.decode(WatcherCodec.encode(doc)))
        assertFalse(WatcherCodec.payloadFor(r,true).has("poster"))
        assertFalse(WatcherCodec.payloadFor(r,true).has("pendingAction"))
    }

    @Test fun pausingDoesNotAcknowledgeAnUnsentQualityEdit()=runBlocking{
        val p=Memory(rule().copy(pendingAction="save",state=WatchRuleModel.STATE_SAVED_LOCAL,hdrMode=WatchModes.MODE_MANDATORY))
        WatchRules.setPaused(id,true,p,client{_,_,_->JsonObject()})
        assertEquals("save",p.doc.rules.single().pendingAction)
        assertEquals("Pending sync",autoAddMode(p.doc.rules.single()))
    }

    @Test fun changingWatcherInvalidatesOldConfirmationsWithoutReplayingRules()=runBlocking{
        val p=Memory(rule())
        WatchRules.saveConnection("https://other.example/","another-token-123456789",persistence=p)
        assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,p.doc.rules.single().state)
        assertEquals(id,p.doc.rules.single().ruleId)
        assertTrue(p.doc.statuses.isEmpty())
    }

    @Test fun statusRowsUseServerTimesAndHumanReadableLabels(){
        val rows=watchEpisodes(JsonParser.parseString("""[{"season":1,"episode_number":3,"status":"waiting_torbox_cache","last_check":100,"next_check":200,"status_detail":"https://secret.invalid/?apikey=never-show"}]""").asJsonArray)
        assertEquals("S01E03",rows.single().label);assertEquals(100000,rows.single().lastCheck)
        assertEquals("Waiting for TorBox cache",watchStatusLabel(rows.single().status))
        assertFalse(watchEpisodeReason(rows.single()).contains("apikey"))
    }

    @Test fun attentionAndInProgressFiltersDoNotHidePausedOrPendingRules(){
        val r=rule();val s=WatchRuleStatus(id,"Example",false,true,1,mapOf("waiting_torbox_cache" to 1),1)
        assertTrue(watchInProgress(r,s));assertFalse(watchInProgress(r.copy(paused=true),s))
        assertTrue(watchNeedsAttention(r.copy(pendingAction="off",state=WatchRuleModel.STATE_SAVED_LOCAL),s))
        assertFalse(watchNeedsAttention(r,s))
    }

    @Test fun legacySeriesTabMigratesToLibraryWithoutDuplicates(){
        assertEquals(listOf("home","library","account"),NavigationPreferences.normalize(listOf("home","series","library","account")))
        assertFalse("series" in NavigationPreferences.defaults)
    }

    @Test fun usefulEpisodePrioritizesFailuresAndPendingPublication(){
        fun row(status:String,n:Int)=WatchEpisode(1,n,status,"",0,0,null)
        assertEquals("connection_unavailable",watchFeatured(listOf(row("waiting_release",1),row("connection_unavailable",3)))?.status)
        assertEquals("waiting_library",watchFeatured(listOf(row("waiting_library",2),row("available_jellyfin",1)))?.status)
    }
}
