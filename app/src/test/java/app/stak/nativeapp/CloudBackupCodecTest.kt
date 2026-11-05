package app.stak.nativeapp

import app.stak.nativeapp.data.*
import com.google.gson.*
import org.junit.Assert.*
import org.junit.Test

class CloudBackupCodecTest {
    private val uid="00000000-0000-4000-8000-000000000001"
    private val other="00000000-0000-4000-8000-000000000002"
    private val key=ByteArray(32){it.toByte()}
    private fun data()=JsonObject().apply{addProperty("secret","private-local-value");addProperty("history","observed history")}
    @Test fun usernameIsNormalizedAndDoesNotRequireAnEmail(){assertEquals("alice_1",CloudBackupCodec.username(" Alice_1 "));assertEquals(CloudBackupCodec.identity("Alice_1"),CloudBackupCodec.identity("alice_1"));assertTrue(CloudBackupCodec.identity("alice_1").endsWith(".invalid"))}
    @Test fun invalidUsernamesAreRejected(){listOf("", "aa", "some@email", "../../user", "x".repeat(33)).forEach{assertTrue(runCatching{CloudBackupCodec.username(it)}.isFailure)}}
    @Test fun passwordsAreNotSilentlyTrimmedOrTruncated(){CloudBackupCodec.password(" valid password ");listOf("short", "x".repeat(73),"é".repeat(37)).forEach{assertTrue(runCatching{CloudBackupCodec.password(it)}.isFailure)}}
    @Test fun encryptedCompressedDataRoundTrips(){val source=data();val envelope=CloudBackupCodec.seal(source,key,uid);assertFalse(envelope.toString().contains("private-local-value"));assertEquals(source,CloudBackupCodec.open(envelope,key,uid))}
    @Test fun encryptionUsesUniqueNonces(){val source=data();val one=CloudBackupCodec.seal(source,key,uid);val two=CloudBackupCodec.seal(source,key,uid);assertNotEquals(one,two);assertEquals(CloudBackupCodec.fingerprint(CloudBackupCodec.open(one,key,uid)),CloudBackupCodec.fingerprint(CloudBackupCodec.open(two,key,uid)))}
    @Test fun anotherAccountCannotOpenTheCiphertext(){val envelope=CloudBackupCodec.seal(data(),key,uid);assertTrue(runCatching{CloudBackupCodec.open(envelope,key,other)}.isFailure)}
    @Test fun incorrectKeysCannotOpenTheCiphertext(){val envelope=CloudBackupCodec.seal(data(),key,uid);assertTrue(runCatching{CloudBackupCodec.open(envelope,ByteArray(32),uid)}.isFailure)}
    @Test fun modifiedCiphertextIsRejected(){val envelope=CloudBackupCodec.seal(data(),key,uid);val bytes=java.util.Base64.getDecoder().decode(envelope["data"].asString);bytes[0]=(bytes[0].toInt()xor 1).toByte();envelope.addProperty("data",java.util.Base64.getEncoder().encodeToString(bytes));assertTrue(runCatching{CloudBackupCodec.open(envelope,key,uid)}.isFailure)}
    @Test fun unsupportedEnvelopeVersionIsRejected(){val envelope=CloudBackupCodec.seal(data(),key,uid);envelope.addProperty("format",2);assertTrue(runCatching{CloudBackupCodec.open(envelope,key,uid)}.isFailure)}
    @Test fun fingerprintIsStableAcrossObjectKeyOrdering(){val a=JsonParser.parseString("{\"b\":2,\"a\":1}").asJsonObject;val b=JsonParser.parseString("{\"a\":1,\"b\":2}").asJsonObject;assertEquals(CloudBackupCodec.fingerprint(a),CloudBackupCodec.fingerprint(b))}
    @Test fun keyDerivationSurvivesReinstallAndSeparatesAccounts(){val password="test-password".toCharArray();val one=CloudBackupCodec.key(password,uid);assertTrue(password.all{it=='\u0000'});assertArrayEquals(one,CloudBackupCodec.key("test-password".toCharArray(),uid));assertFalse(one.contentEquals(CloudBackupCodec.key("test-password".toCharArray(),other)))}
    @Test fun oversizedSnapshotsAreNotSilentlyTruncated(){val source=JsonObject().apply{addProperty("huge","x".repeat(CloudBackupCodec.MAX_PLAIN))};assertTrue(runCatching{CloudBackupCodec.seal(source,key,uid)}.isFailure)}
    @Test fun preferenceTypesSurvivePortableSerialization(){val input=mapOf("string" to "text","long" to 123456789012L,"int" to 2,"float" to .4f,"bool" to true,"set" to setOf("b","a"));assertEquals(input,decodeBackupPreferences(encodeBackupPreferences(input)))}
    @Test fun cloudTokensDeviceIdsAndCryptoKeysetsAreNotBackupData(){listOf("cloud_session","cloud_restore_recovery","jellyfin_device_id","last_reminder_at","__androidx_security_crypto_encrypted_prefs_key_keyset__").forEach{assertFalse(cloudBackupKey(it))};assertTrue(cloudBackupKey("jellyfin_url"))}
    @Test fun importedPreferencesCannotOverwriteTheCloudSession(){val value=JsonParser.parseString("{\"cloud_session\":{\"type\":\"string\",\"value\":\"other\"}}").asJsonObject;assertTrue(runCatching{decodeBackupPreferences(value)}.isFailure)}
}
