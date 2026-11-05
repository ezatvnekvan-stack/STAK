package app.stak.nativeapp

import app.stak.nativeapp.data.*
import com.google.gson.*
import org.junit.*
import org.junit.Assert.*
import java.net.*
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

class CloudAccountProtocolTest {
    data class Call(val path:String,val headers:Map<String,String>,val body:String)
    data class Reply(val body:String="{}",val status:Int=200,val extra:String="")
    class Server(private val reply:(Call)->Reply):Closeable {
        private val server=ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));val calls=CopyOnWriteArrayList<Call>();val url="http://127.0.0.1:${server.localPort}"
        private val thread=Thread{
            try{while(!server.isClosed)server.accept().use{socket->
                socket.soTimeout=5000;val input=socket.getInputStream().bufferedReader();val first=input.readLine()?:return@use
                val headers=mutableMapOf<String,String>();while(true){val line=input.readLine()?:break;if(line.isEmpty())break;headers[line.substringBefore(':').lowercase()]=line.substringAfter(':').trim()}
                val length=headers["content-length"]?.toIntOrNull()?:0;val chars=CharArray(length);var count=0;while(count<length){val n=input.read(chars,count,length-count);if(n<0)break;count+=n}
                val call=Call(first.split(' ')[1],headers,String(chars));calls+=call
                val response=reply(call);val bytes=response.body.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply{write(("HTTP/1.1 ${response.status} Test\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${response.extra}\r\n").toByteArray());write(bytes);flush()}
            }}catch(_:java.io.IOException){}
        }.apply{isDaemon=true;start()}
        fun api()=CloudAccountApi(CloudConfig(url,"sb_publishable_synthetic"),true)
        override fun close(){server.close();thread.join(1000)}
    }
    private val uid="00000000-0000-4000-8000-000000000001"
    private val auth=CloudAuth(uid,"fixture_user","synthetic-access","synthetic-refresh",Long.MAX_VALUE)
    private fun authReply(id:String=uid)=JsonObject().apply{addProperty("access_token","synthetic-access");addProperty("refresh_token","synthetic-refresh");addProperty("expires_in",3600);add("user",JsonObject().apply{addProperty("id",id);addProperty("email",CloudBackupCodec.identity("fixture_user"))})}.toString()

    @Test fun usernameOnlyRegistrationUsesPasswordAuthWithoutSendingMail(){
        Server{call->when(call.path){"/auth/v1/settings"->Reply("{\"mailer_autoconfirm\":true,\"external\":{\"email\":true}}") ;else->Reply(authReply())}}.use{server->
            val result=server.api().authenticate("Fixture_User","synthetic-password",true)
            assertEquals(uid,result.userId);assertEquals(listOf("/auth/v1/settings","/auth/v1/signup"),server.calls.map{it.path})
            val body=JsonParser.parseString(server.calls.last().body).asJsonObject
            assertEquals(CloudBackupCodec.identity("fixture_user"),body["email"].asString);assertEquals("synthetic-password",body["password"].asString)
            assertTrue(server.calls.all{it.headers["apikey"]=="sb_publishable_synthetic"&&it.headers["authorization"]==null})
        }
    }
    @Test fun emailConfirmationConfigurationBlocksRegistrationBeforeCreatingAnything(){
        Server{Reply("{\"mailer_autoconfirm\":false,\"external\":{\"email\":true}}")}.use{server->
            val error=runCatching{server.api().authenticate("fixture_user","synthetic-password",true)}.exceptionOrNull()
            assertTrue(error is CloudFailure);assertEquals(1,server.calls.size);assertEquals("/auth/v1/settings",server.calls.single().path)
        }
    }
    @Test fun missingSessionNeverCountsAsSuccessfulSignIn(){
        Server{call->if(call.path.endsWith("settings"))Reply("{\"mailer_autoconfirm\":true,\"external\":{\"email\":true}}")else Reply("{\"user\":{\"id\":\"$uid\",\"email\":\"${CloudBackupCodec.identity("fixture_user")}\"}}")}.use{server->assertTrue(runCatching{server.api().authenticate("fixture_user","synthetic-password",false)}.isFailure)}
    }
    @Test fun backupRequestsUseUserJwtAndDoNotSendPasswordOrAnArbitraryUserId(){
        Server{Reply("{\"revision\":0,\"backup\":null,\"fingerprint\":null,\"saved_at\":null}")}.use{server->
            assertEquals(0L,server.api().read(auth).revision);val call=server.calls.single()
            assertEquals("/rest/v1/rpc/stak_backup_read",call.path);assertEquals("Bearer synthetic-access",call.headers["authorization"]);assertEquals("{}",call.body)
        }
    }
    @Test fun credentialedRedirectsAreNeverFollowed(){
        Server{Reply()}.use{other->Server{Reply(status=302,extra="Location: ${other.url}/steal\r\n")}.use{server->
            assertTrue(runCatching{server.api().read(auth)}.isFailure);assertTrue(other.calls.isEmpty());assertEquals(1,server.calls.size)
        }}
    }
    @Test fun staleBackupWriteDoesNotRetryAndExposesNoServerBody(){
        Server{Reply("{\"conflict\":true}")}.use{server->
            val error=runCatching{server.api().write(auth,1,"a".repeat(64),JsonObject(),java.util.UUID.randomUUID().toString())}.exceptionOrNull()
            assertTrue(error is CloudFailure);assertEquals("conflict",(error as CloudFailure).kind);assertEquals(1,server.calls.size)
        }
    }
    @Test fun failureDoesNotExposeCredentialsOrFabricateSavedState(){
        Server{Reply("{\"error\":\"private-server-secret\"}",401)}.use{server->
            val error=runCatching{server.api().read(auth)}.exceptionOrNull()!!;assertFalse(error.message.orEmpty().contains("private-server-secret"));assertTrue(error is CloudFailure)
        }
    }
    @Test fun refreshRejectsUserChanges(){
        Server{Reply(authReply("00000000-0000-4000-8000-000000000002"))}.use{server->assertTrue(runCatching{server.api().refresh(auth)}.isFailure);assertEquals("/auth/v1/token?grant_type=refresh_token",server.calls.single().path)}
    }
    @Test fun productionTransportRejectsPlainHttpBeforeSendingAnything(){
        Server{Reply()}.use{server->assertTrue(runCatching{CloudAccountApi(CloudConfig(server.url,"sb_publishable_synthetic")).read(auth)}.isFailure);assertTrue(server.calls.isEmpty())}
    }
}
