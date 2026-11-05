package app.stak.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.stak.nativeapp.data.*
import app.stak.nativeapp.ui.screens.LibraryAccessDialog
import app.stak.nativeapp.ui.theme.StakTheme
import com.google.gson.*
import org.junit.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Emulator-only loopback service; never accesses an owner account. */
class UnifiedLibraryUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var socket:ServerSocket
    private lateinit var worker:Thread
    private var previous:SettingsStore?=null
    private lateinit var prefix:String
    private val calls=CopyOnWriteArrayList<String>()
    private val remote=AtomicReference<JsonObject>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup(){
        previous=runCatching{NativeRuntime.settings}.getOrNull()
        prefix="unified-server-ui-${java.util.UUID.randomUUID()}-"
        NativeRuntime.settings=SettingsStore(context,prefix).apply{torboxKey="fixture-only-torbox";reducedMotion=true}
        socket=ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1"))
        remote.set(JsonObject().apply{
            addProperty("id","123");addProperty("scope",torboxScope());addProperty("hash","a".repeat(40))
            addProperty("approved",true);addProperty("type","series");addProperty("name","VisionQuest")
            addProperty("state","published");addProperty("note","Organized library; verified source files")
            add("media",JsonParser.parseString("""{"title":"VisionQuest","imdb":"tt23112594","year":2026}"""))
        })
        worker=Thread{
            while(!socket.isClosed){try{socket.accept().use{client->
                client.soTimeout=5000
                val input=client.getInputStream().bufferedReader()
                val request=input.readLine()?:return@use
                calls+=request
                var length=0
                while(true){val line=input.readLine()?:break;if(line.isEmpty())break;if(line.startsWith("Content-Length:",true))length=line.substringAfter(':').trim().toInt()}
                val body=CharArray(length);var offset=0
                while(offset<length){val n=input.read(body,offset,length-offset);if(n<0)break;offset+=n}
                val path=request.split(' ')[1]
                val result=when{
                    path.endsWith("/v1/status")->JsonObject().apply{addProperty("protocol","stak-library-filter/1");addProperty("server_protocol","stak-server/1");addProperty("layout","organized");addProperty("scope",torboxScope());addProperty("source_available",true);addProperty("jellyfin_paths_verified",true)}
                    request.startsWith("POST")&&path.endsWith("/v1/identity")->{val update=JsonParser.parseString(String(body)).asJsonObject;remote.set(remote.get().deepCopy().apply{add("media",update.get("media"))});remote.get()}
                    else->JsonObject().apply{add("items",JsonArray().apply{add(remote.get())})}
                }
                val bytes=result.toString().toByteArray()
                client.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()+bytes)
            }}catch(_:Exception){if(socket.isClosed)break}}
        }.apply{isDaemon=true;start()}
        ApprovalStore().update{it.copy(connection=LibraryFilterConnection("http://127.0.0.1:${socket.localPort}","fixture-token-"+"a".repeat(32),true))}
    }
    @After fun cleanup(){
        socket.close();worker.join(1000)
        previous?.let{NativeRuntime.settings=it}
        listOf("stak_settings","stak_secrets").forEach{context.deleteSharedPreferences(prefix+it)}
    }
    private fun capture(name:String){
        val base=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:context.getExternalFilesDir(null)!!.path
        val dir=File(base,"unified-server-ui");dir.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let{bitmap->File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    }
    @Test fun watcherApprovalIsVisibleAndIdentityEditNeverReapproves(){
        compose.setContent{StakTheme{Box(Modifier.fillMaxSize()){LibraryAccessDialog{}}}}
        compose.waitUntil(10000){compose.onAllNodesWithText("Published · organized").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("1 approved").assertExists()
        compose.onNodeWithText("Published · organized").assertExists()
        compose.onNodeWithText("Identify").performScrollTo().performClick()
        compose.onNodeWithText("Identify title").assertIsDisplayed()
        compose.onNodeWithText("tt23112594").assertIsDisplayed()
        capture("identify-title")
        compose.onNode(hasSetTextAction() and hasText("VisionQuest")).performTextReplacement("VisionQuest verified")
        compose.onNodeWithText("SAVE IDENTITY").performClick()
        compose.waitUntil(10000){remote.get().getAsJsonObject("media").get("title").asString=="VisionQuest verified"}
        Assert.assertTrue(remote.get().get("approved").asBoolean)
        Assert.assertFalse(calls.any{it.startsWith("POST")&&!it.contains("/v1/identity")})
        capture("remote-approval-imported")
    }
}
