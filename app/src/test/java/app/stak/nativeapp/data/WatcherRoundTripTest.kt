package app.stak.nativeapp.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Phase 5: real HTTP round trips between the Android watcher client and a
 * loopback stub that re-implements stak-watch's exact API habits (Bearer
 * STAK_WATCHER_TOKEN, explicit consent gate, canonical rule responses,
 * DELETE = pause + keep history). Isolated fixtures only — nothing touches a
 * live TorBox account, and the app never sends any add (cached-only lives
 * entirely server-side).
 */
class WatcherRoundTripTest {

    /** Minimal stand-in stak-watch HTTP API on 127.0.0.1 (one request per connection). */
    private class WatcherStub{        val token="STAK_WATCHER_TOKEN_TEST_12345"
        val rules=LinkedHashMap<String,JsonObject>()
        val episodeRows=LinkedHashMap<String,MutableList<JsonObject>>()
        val calls=MutableList(0){Triple("","","")}
        @Volatile var dropConnections=false
        private lateinit var server:ServerSocket
        private val pool=Executors.newFixedThreadPool(2)
        lateinit var base:String;private set

        fun start(){
            server=ServerSocket(0,8,InetAddress.getByName("127.0.0.1"))
            base="http://127.0.0.1:${server.localPort}/"
            val acceptor=java.lang.Thread({
                try{while(!server.isClosed){val client=server.accept();pool.execute{serve(client)}}}
                catch(_:Exception){} // socket closed on stop()
            })
            acceptor.isDaemon=true;acceptor.name="watcher-stub";acceptor.start()
        }
        fun stop(){try{server.close()}catch(_:Exception){}}
        fun rulesOnServerCount():Int=rules.size

        private fun serve(client:Socket){
            if(dropConnections){client.close();return} // real TCP dropout; port remains owned by this fixture
            client.use{socket->
                val input=socket.getInputStream().buffered()
                val requestLine=input.readLine()?:return@use
                val parts=requestLine.split(" ")
                if(parts.size<2)return@use
                var length=0;var authorization:String?=null
                while(true){
                    val header=input.readLine()?:break
                    if(header.isBlank())break
                    if(header.startsWith("Content-Length:",true))length=header.substringAfter(':').trim().toIntOrNull()?:0
                    if(header.startsWith("Authorization:",true))authorization=header.substringAfter(':').trim()
                }
                val body=if(length>0)input.readFully(length) else null
                try{dispatch(parts[0],parts[1],authorization,body,socket.getOutputStream())}
                finally{socket.getOutputStream().flush()}
            }
        }

        private fun dispatch(method:String,full:String,authorization:String?,body:String?,out:OutputStream){
            calls+=Triple(method,full.takeBefore('?'),body?:String())
            val path=full.takeBefore('?')
            if(path=="/health"){
                reply(out,200,JsonObject().apply{addProperty("ok",true);addProperty("version","1.1.0")});return
            }
            if(authorization!="Bearer $token"){
                reply(out,401,json("""{"error":"unauthorized"}"""));return
            }
            when{
                method=="GET"&&path=="/v1/status"->reply(out,200,json(
                    """{"status":"ok","rules":[]}"""))
                method=="GET"&&path.startsWith("/v1/rules/")&&!path.endsWith("/pause")&&!path.endsWith("/resume")->{
                    val id=path.removePrefix("/v1/rules/")
                    val rule=rules[id]
                    if(rule==null)reply(out,404,json("""{"error":"no such rule"}"""))
                    else reply(out,200,rule) // canonical rule object, not wrapped in "rule"
                }
                method=="GET"&&path=="/v1/episodes"->reply(out,200,JsonObject().apply{
                    add("items",JsonArray().apply{episodeRows[full.takeAfter("rule_id=")].orEmpty().forEach{add(it)}})})
                method=="POST"&&path=="/v1/rules"->{
                    val payload=JsonParser.parseString(body).asJsonObject
                    if(payload.getAsJsonObject("series")==null){
                        reply(out,400,json("""{"error":"series object required"}"""));return
                    }
                    // The consent gate: watchlisting alone must not enable automation.
                    if(payload.get("confirm")?.takeIf{it.isJsonPrimitive}?.asBoolean!=true&&
                        payload.get("confirmed")?.takeIf{it.isJsonPrimitive}?.asBoolean!=true){
                        reply(out,400,json("""{"error":"auto-add must be explicitly confirmed by the user"}"""));return
                    }
                    val canonical=payload.deepCopy();canonical.addProperty("confirmed",true)
                    val id=canonical.get("ruleId").asString
                    rules[id]=canonical
                    episodeRows.putIfAbsent(id,mutableListOf<JsonObject>())
                    episodeRows[id]?.add(if(canonical.get("paused")?.asBoolean==true)
                        json("""{"status":"paused"}""") else json("""{"status":"waiting_release"}"""))
                    reply(out,201,JsonObject().apply{add("rule",canonical);add("issues",JsonArray());addProperty("episodes",1)})
                }
                method=="POST"&&path.startsWith("/v1/rules/")&&(path.endsWith("/pause")||path.endsWith("/resume"))->{
                    val id=path.removePrefix("/v1/rules/").removeSuffix("/pause").removeSuffix("/resume")
                    val rule=rules[id]
                    if(rule==null){reply(out,404,json("""{"error":"no such rule"}"""));return}
                    val paused=path.endsWith("/pause")
                    rule.addProperty("paused",paused)
                    reply(out,200,JsonObject().apply{add("rule",rule.deepCopy())})
                }
                method=="DELETE"&&path.startsWith("/v1/rules/")->{
                    rules.remove(path.removePrefix("/v1/rules/"))
                    // Pause rather than delete: history and ledger are preserved.
                    reply(out,200,json("""{"paused":true,"note":"history kept"}"""))
                }
                else->reply(out,404,json("""{"error":"unknown operation"}"""))
            }
        }

        private fun reply(out:OutputStream,status:Int,body:JsonObject){
            val blob=body.toString().encodeToByteArray()
            val head="HTTP/1.1 $status OK\r\nContent-Type: application/json\r\nContent-Length: ${blob.size}\r\nConnection: close\r\n\r\n"
            out.write(head.encodeToByteArray());out.write(blob);out.flush()
        }
        private fun json(raw:String)=JsonParser.parseString(raw).asJsonObject
    }

    private class FakePersistence(start:WatcherDocument=WatcherDocument()):WatchRulePersistence{
        var doc:WatcherDocument=start
        override fun read():WatcherDocument=doc
        override fun update(change:(WatcherDocument)->WatcherDocument):WatcherDocument{doc=change(doc);return doc}
    }

    private val stub=WatcherStub()
    private fun paired():FakePersistence=FakePersistence(WatcherDocument(
        connection=WatcherConnection(stub.base,stub.token,enabled=true)))
    private fun clientFactory(token:String=stub.token):(WatcherConnection)->WatcherClient=
        {_->WatcherClient(WatcherConnection(stub.base,token),HttpWatcherTransport())}
    private val key="imdb:tt0111161";private val ruleId=watchRuleIdFor(key)
    private fun rule()=WatchRuleModel(ruleId=ruleId,seriesImdb="tt0111161",seriesName="Sample Series",
        enabled=true,watchedSeasons=listOf(1),releaseTypes=listOf(WatchModes.RELEASE_WEBDL))

    /** The app may only ever call watcher API paths — never TorBox endpoints. */
    private fun assertWatcherPathsOnly(){
        stub.calls.forEach{(method,path,_)->
            assertTrue("Unexpected app call: $method $path",path=="/health"||path.startsWith("/v1/"))
        }
    }

    @Test fun pairingProbeHandshakesWithWatcherHealth()=runBlocking{
        stub.start()
        try{
            val message=WatchRules.probe(WatcherConnection(stub.base,stub.token))
            assertTrue(message.contains("1.1.0"));assertTrue(message.contains("paired"))
        }finally{stub.stop()}
        assertWatcherPathsOnly()
    }

    @Test fun fullRuleLifecycleCooperatesWithWatcherApi()=runBlocking{
        stub.start()
        try{
            val persistence=paired()
            // 1) Save with consent: HTTP 201, server stores the rule.
            val saved=WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=clientFactory())
            assertTrue(saved.confirmed)
            assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,saved.rule.state)
            assertTrue(stub.rules.containsKey(ruleId))
            assertEquals(true,JsonParser.parseString(stub.calls.last {it.first=="POST"&&it.second=="/v1/rules"}.third).asJsonObject.get("confirm").asBoolean)
            assertEquals("Sample Series",stub.rules[ruleId]!!.getAsJsonObject("series").get("name").asString)

            // 2) Status sync reads the canonical (unwrapped) rule + episode rows.
            val doc=WatchRules.refresh(persistence=persistence,clientFactory=clientFactory())
            assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,doc.rules.single().state)
            val status=doc.statuses.single()
            assertEquals(1,status.episodes);assertEquals(1,status.statuses["waiting_release"])
            assertFalse(status.paused)

            // 3) Pause and resume: the watcher's paused flag is the truth.
            WatchRules.setPaused(key,true,persistence=persistence,clientFactory=clientFactory())
            assertEquals(true,stub.rules[ruleId]!!.get("paused").asBoolean)
            WatchRules.setPaused(key,false,persistence=persistence,clientFactory=clientFactory())
            assertEquals(false,stub.rules[ruleId]!!.get("paused").asBoolean)

            // 4) Turn off: DELETE pauses the watcher rows (history kept server-side).
            WatchRules.turnOff(key,persistence=persistence,clientFactory=clientFactory())
            assertFalse(stub.rules.containsKey(ruleId))
            assertTrue(stub.episodeRows[ruleId]!!.isNotEmpty())
            assertTrue(persistence.doc.rules.isEmpty())
        }finally{stub.stop()}
        assertWatcherPathsOnly()
    }

    @Test fun connectionDropoutKeepsLocalRuleAndExplainsIt()=runBlocking{
        stub.start();val persistence=paired()
        try{
            WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=clientFactory())
            stub.dropConnections=true // preserve ownership of the ephemeral port while interrupting real HTTP
            WatchRules.setPaused(key,true,persistence=persistence,clientFactory=clientFactory())
            val issue=WatchRules.issue.value
            assertNotNull("The dropout must be explained to the user",issue)
            assertTrue("Expected a watcher connection explanation, got: $issue",issue!!.lowercase().contains("watcher"))
            assertTrue(issue.none{it.code<32}) // readable text, no control characters
            // An unacknowledged pause remains visible as pending.
            assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,persistence.doc.rules.single().state)
            // Offline saves stay saved_local until the watcher answers.
            val offline=WatchRules.saveRule(
                rule().copy(ruleId=watchRuleIdFor("tvdb:1"),seriesTvdb="1",seriesName="Other Series"),
                consent=true,persistence=persistence,clientFactory=clientFactory())
            assertFalse("A dropped connection must never confirm a rule",offline.confirmed)
            assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,offline.rule.state)
        }finally{try{stub.stop()}catch(_:Exception){}}
    }

    @Test fun restartAndRestoreIsPassiveAndNeverDuplicatesServerWork()=runBlocking{
        stub.start();val persistence=paired()
        try{
            WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=clientFactory())
            val before=stub.rulesOnServerCount()
            val postCount=stub.calls.count{it.first=="POST"&&it.second=="/v1/rules"}
            val restoredDtoJson=WatcherCodec.encode(persistence.doc)

            // Simulated restore (reinstall / new device / cloud restore: same JSON).
            val restored=FakePersistence(WatcherCodec.decode(restoredDtoJson))
            assertEquals(listOf(ruleId),restored.doc.rules.map{it.ruleId}) // stable identity

            // The watcher restarted too: same token, same store content.
            WatchRules.refresh(persistence=restored,clientFactory=clientFactory())
            assertEquals("Restore must never create duplicate server rules",before,stub.rulesOnServerCount())
            assertEquals("Restore must never POST rules",postCount,stub.calls.count{it.first=="POST"&&it.second=="/v1/rules"})
            // The restored rule is re-confirmed by reading only.
            assertEquals(WatchRuleModel.STATE_SERVER_CONFIRMED,restored.doc.rules.single().state)
        }finally{stub.stop()}
        assertWatcherPathsOnly()
    }

    @Test fun whenWatcherLostTheRuleRestoreStaysLocalOnly()=runBlocking{
        stub.start();val persistence=paired()
        try{
            WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=clientFactory())
            val postCount=stub.calls.count{it.first=="POST"&&it.second=="/v1/rules"}
            stub.rules.clear() // a fresh watcher install does not know the rule
            val restored=FakePersistence(WatcherCodec.decode(WatcherCodec.encode(persistence.doc)))
            WatchRules.refresh(persistence=restored,clientFactory=clientFactory())
            // No duplicate re-save; the local copy keeps its state and identity.
            assertTrue(stub.rules.isEmpty())
            assertEquals(0,stub.calls.count{it.first=="POST"&&it.second=="/v1/rules"}-postCount)
            assertEquals(ruleId,restored.doc.rules.single().ruleId)
        }finally{stub.stop()}
        assertWatcherPathsOnly()
    }

    @Test fun wrongTokenIsRejectedWithActionableMessage()=runBlocking{
        stub.start()
        try{
            val persistence=paired()
            val wrong=clientFactory("anything-but-the-real-token")
            // The facade maps the server's 401 to a readable issue and keeps the
            // rule in the saved-on-device state (never "confirmed").
            WatchRules.saveRule(rule(),consent=true,persistence=persistence,clientFactory=wrong)
            assertTrue(WatchRules.issue.value.orEmpty().contains("token"))
            assertEquals(WatchRuleModel.STATE_SAVED_LOCAL,persistence.doc.rules.single().state)
            assertFalse(stub.rules.containsKey(ruleId))
        }finally{stub.stop()}
        assertWatcherPathsOnly()
    }
}

private fun String.takeBefore(c:Char)=substringBefore(c)
private fun String.takeAfter(c:String)=substringAfter(c)
private fun InputStream.readLine():String?{
    val buffer=StringBuilder()
    while(true){
        val c=read();if(c<0)return if(buffer.isEmpty())null else buffer.toString()
        if(c.toChar()=='\n')return buffer.toString().trimEnd('\r')
        buffer.append(c.toChar())
    }
}
private fun InputStream.readFully(count:Int):String{
    val bytes=ByteArray(count);var off=0
    while(off<count){val n=read(bytes,off,count-off);if(n<=0)break;off+=n}
    return String(bytes,0,off,Charsets.UTF_8)
}
