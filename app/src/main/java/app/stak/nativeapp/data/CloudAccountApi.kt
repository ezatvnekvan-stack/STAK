package app.stak.nativeapp.data

import app.stak.nativeapp.BuildConfig
import com.google.gson.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

data class CloudConfig(val url:String,val publicKey:String){
    val configured get()=url.isNotBlank()&&publicKey.isNotBlank()
    val scope get()=CloudBackupCodec.digest((url.trimEnd('/')+"|"+publicKey).toByteArray())
    companion object{fun bundled()=CloudConfig(BuildConfig.STAK_CLOUD_URL,BuildConfig.STAK_CLOUD_PUBLIC_KEY)}
}
class CloudFailure(val kind:String,val safeMessage:String):IOException(safeMessage)
data class CloudAuth(val userId:String,val username:String,val accessToken:String,val refreshToken:String,val expiresAt:Long)
data class CloudRemote(val revision:Long,val fingerprint:String?,val envelope:JsonObject?,val savedAt:Long)
interface CloudAccountGateway {
    val config:CloudConfig
    fun authenticate(username:String,password:String,create:Boolean):CloudAuth
    fun refresh(auth:CloudAuth):CloudAuth
    fun read(auth:CloudAuth):CloudRemote
    fun write(auth:CloudAuth,expected:Long,fingerprint:String,envelope:JsonObject,operation:String):CloudRemote
    fun logout(auth:CloudAuth)
}
class CloudAccountApi(override val config:CloudConfig=CloudConfig.bundled(),private val allowLoopbackForTest:Boolean=false):CloudAccountGateway {
    private val http=OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).build()
    private fun request(path:String,body:JsonObject?=null,auth:CloudAuth?=null):JsonObject {
        if(!config.configured)throw CloudFailure("unconfigured","Account service is not set up yet.")
        val base=config.url.toHttpUrl()
        require(base.username.isEmpty()&&base.password.isEmpty()&&base.query==null&&base.fragment==null&&base.encodedPath.trimEnd('/').isEmpty())
        require(base.isHttps||(allowLoopbackForTest&&base.host in setOf("127.0.0.1","localhost")))
        require(config.publicKey.startsWith("sb_publishable_")||allowLoopbackForTest)
        val builder=Request.Builder().url(base.newBuilder().encodedPath(path.substringBefore('?')).apply{path.substringAfter('?',"").takeIf{it.isNotBlank()}?.let{encodedQuery(it)}}.build()).header("apikey",config.publicKey).header("Content-Type","application/json")
        auth?.let{builder.header("Authorization","Bearer ${it.accessToken}")}
        if(body!=null){val raw=body.toString();require(raw.toByteArray(Charsets.UTF_8).size<=CloudBackupCodec.MAX_ENVELOPE+8192);builder.post(raw.toRequestBody("application/json".toMediaType()))}
        return http.newCall(builder.build()).execute().use{response->
            if(!response.isSuccessful)throw when(response.code){
                401,403->CloudFailure("auth","Sign in again to continue saving.")
                409->CloudFailure("conflict","A newer backup exists. Sign in again to restore it.")
                429->CloudFailure("rate","Too many attempts. Try again shortly.")
                else->CloudFailure("server",if(path.contains("/auth/"))"Could not sign in. Check your username and password."else "Could not save right now. Your local data is unchanged.")
            }
            if(response.code==204)return@use JsonObject()
            val stream=response.body?.byteStream()?:throw IOException("Missing response")
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=stream.read(buffer);if(n<0)break;require(out.size()+n<=CloudBackupCodec.MAX_ENVELOPE+16384);out.write(buffer,0,n)}
            JsonParser.parseString(out.toString("UTF-8")).asJsonObject
        }
    }
    private fun parseAuth(json:JsonObject,name:String,expectedId:String?=null):CloudAuth {
        val user=json.getAsJsonObject("user")?:throw CloudFailure("configuration","Account service configuration needs attention.")
        val id=user.get("id").asString;UUID.fromString(id)
        require(user.get("email")?.asString==CloudBackupCodec.identity(name))
        require(expectedId==null||id==expectedId)
        val access=json.get("access_token")?.asString.orEmpty();val refresh=json.get("refresh_token")?.asString.orEmpty()
        if(access.isBlank()||refresh.isBlank())throw CloudFailure("configuration","Account service configuration needs attention.")
        val expiry=json.get("expires_in")?.asLong?:3600L;require(expiry in 1..604800)
        return CloudAuth(id,name,access,refresh,System.currentTimeMillis()+expiry*1000)
    }
    override fun authenticate(username:String,password:String,create:Boolean):CloudAuth {
        val name=CloudBackupCodec.username(username);CloudBackupCodec.password(password)
        val settings=request("/auth/v1/settings")
        if(settings.get("mailer_autoconfirm")?.asBoolean!=true||settings.getAsJsonObject("external")?.get("email")?.asBoolean!=true)throw CloudFailure("configuration","Account service configuration needs attention.")
        val payload=JsonObject().apply{addProperty("email",CloudBackupCodec.identity(name));addProperty("password",password);if(create)add("data",JsonObject().apply{addProperty("stak_username",name)})}
        return parseAuth(request(if(create)"/auth/v1/signup"else "/auth/v1/token?grant_type=password",payload),name)
    }
    override fun refresh(auth:CloudAuth)=parseAuth(request("/auth/v1/token?grant_type=refresh_token",JsonObject().apply{addProperty("refresh_token",auth.refreshToken)}),auth.username,auth.userId)
    private fun remote(json:JsonObject):CloudRemote {
        val revision=json.get("revision")?.asLong?:throw IOException("Invalid backup response");require(revision>=0)
        val blob=json.get("backup")?.takeUnless{it.isJsonNull}?.asJsonObject
        val hash=json.get("fingerprint")?.takeUnless{it.isJsonNull}?.asString
        require((revision==0L&&blob==null)||(revision>0&&blob!=null&&hash?.matches(Regex("[a-f0-9]{64}"))==true))
        val stamp=json.get("saved_at")?.takeUnless{it.isJsonNull}?.asString?.let{java.time.Instant.parse(it).toEpochMilli()}?:0
        require(revision==0L||stamp>0)
        return CloudRemote(revision,hash,blob,stamp)
    }
    override fun read(auth:CloudAuth)=remote(request("/rest/v1/rpc/stak_backup_read",JsonObject(),auth))
    override fun write(auth:CloudAuth,expected:Long,fingerprint:String,envelope:JsonObject,operation:String):CloudRemote {
        val reply=request("/rest/v1/rpc/stak_backup_write",JsonObject().apply{addProperty("p_expected_revision",expected);addProperty("p_fingerprint",fingerprint);add("p_backup",envelope);addProperty("p_operation",operation)},auth)
        if(reply.get("conflict")?.asBoolean==true)throw CloudFailure("conflict","A newer backup exists. Sign in again to restore it.")
        return remote(reply).also{require(it.revision==expected+1&&it.fingerprint==fingerprint&&it.envelope==envelope)}
    }
    override fun logout(auth:CloudAuth){request("/auth/v1/logout?scope=local",JsonObject(),auth)}
}
