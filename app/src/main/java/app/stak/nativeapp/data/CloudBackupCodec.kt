package app.stak.nativeapp.data

import com.google.gson.*
import java.io.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable ciphertext, not a copy of device-bound EncryptedSharedPreferences. */
class CloudInputException(message:String):IllegalArgumentException(message)
object CloudBackupCodec {
    const val MAX_PLAIN=8*1024*1024
    const val MAX_ENVELOPE=6*1024*1024
    private val random=SecureRandom()
    fun username(raw:String):String {
        val value=Normalizer.normalize(raw.trim(),Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        if(!value.matches(Regex("[a-z0-9][a-z0-9._-]{2,31}")))throw CloudInputException("Use 3–32 letters, numbers, dots, underscores or hyphens.")
        return value
    }
    fun password(raw:String){
        if(raw.codePointCount(0,raw.length)<8)throw CloudInputException("Use a password with at least 8 characters.")
        if(raw.toByteArray(Charsets.UTF_8).size>72)throw CloudInputException("Password is too long.")
    }
    // An internal Auth identifier only; never a real mailbox or a user-facing field.
    fun identity(name:String)=digest(username(name).toByteArray(Charsets.UTF_8))+"@accounts.stak.invalid"
    fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt()and 255)}
    fun key(password:CharArray,userId:String):ByteArray {
        UUID.fromString(userId)
        val salt=MessageDigest.getInstance("SHA-256").digest("STAK-BACKUP/1:$userId".toByteArray(Charsets.UTF_8))
        val spec=PBEKeySpec(password,salt,600_000,256)
        return try{SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded}finally{spec.clearPassword();password.fill('\u0000')}
    }
    fun canonical(value:JsonElement):String=when {
        value.isJsonObject->value.asJsonObject.entrySet().sortedBy{it.key}.joinToString(",","{","}"){Gson().toJson(it.key)+":"+canonical(it.value)}
        value.isJsonArray->value.asJsonArray.joinToString(",","[","]"){canonical(it)}
        else->value.toString()
    }
    fun fingerprint(value:JsonObject)=digest(canonical(value).toByteArray(Charsets.UTF_8))
    fun seal(value:JsonObject,key:ByteArray,userId:String):JsonObject {
        require(key.size==32);UUID.fromString(userId)
        val plain=canonical(value).toByteArray(Charsets.UTF_8);require(plain.size<=MAX_PLAIN){"Saved data exceeds the backup limit."}
        val zipped=ByteArrayOutputStream().also{out->GZIPOutputStream(out).use{it.write(plain)}}.toByteArray()
        val nonce=ByteArray(12).also(random::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("STAK-BACKUP/1:$userId".toByteArray(Charsets.UTF_8))
        return JsonObject().apply{addProperty("format",1);addProperty("nonce",Base64.getEncoder().encodeToString(nonce));addProperty("data",Base64.getEncoder().encodeToString(cipher.doFinal(zipped)));require(toString().length<=MAX_ENVELOPE){"Saved data exceeds the backup limit."}}
    }
    fun open(envelope:JsonObject,key:ByteArray,userId:String):JsonObject {
        require(envelope.toString().length<=MAX_ENVELOPE&&envelope.get("format")?.asInt==1&&key.size==32)
        UUID.fromString(userId)
        val nonce=Base64.getDecoder().decode(envelope.get("nonce").asString);require(nonce.size==12)
        val encrypted=Base64.getDecoder().decode(envelope.get("data").asString);require(encrypted.size>=16)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD("STAK-BACKUP/1:$userId".toByteArray(Charsets.UTF_8))
        val zipped=cipher.doFinal(encrypted)
        val plain=GZIPInputStream(ByteArrayInputStream(zipped)).use{input->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_PLAIN);out.write(buffer,0,n)};out.toByteArray()
        }
        return JsonParser.parseString(plain.toString(Charsets.UTF_8)).asJsonObject
    }
}
