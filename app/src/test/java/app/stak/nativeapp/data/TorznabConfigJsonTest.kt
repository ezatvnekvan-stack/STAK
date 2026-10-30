package app.stak.nativeapp.data

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class TorznabConfigJsonTest {
    @Test fun readsPreviouslyWrittenGsonJsonWithoutGenericReflection(){
        val item=TorznabIndexer("stable-id","AIOStreams","https://aio.example/api/v1/torznab/api","uuid:password:with:colons",false)
        assertEquals(listOf(item),TorznabConfigJson.decode(Gson().toJson(listOf(item))))
    }
    @Test fun explicitRoundTripPreservesCredentialsAndDisabledState(){
        val item=TorznabIndexer("stable-id","Árvíz","https://indexer.test/api?token=private","uuid:árvíz\"\\password",false)
        assertEquals(listOf(item),TorznabConfigJson.decode(TorznabConfigJson.encode(listOf(item))))
    }
    @Test fun normalizesEmbeddedApiKeyAndKeepsStableIdentity(){
        val item=TorznabIndexer("stable-id"," AIO ","https://aio.example/api/v1/torznab?apikey=uuid%3Apassword&t=caps")
        val actual=TorznabConfigJson.decode(TorznabConfigJson.encode(listOf(item))).single()
        assertEquals("stable-id",actual.id);assertEquals("AIO",actual.name);assertEquals("uuid:password",actual.apiKey);assertEquals("https://aio.example/api/v1/torznab/api",actual.url)
    }
    @Test fun emptyListRoundTrips(){assertTrue(TorznabConfigJson.decode(TorznabConfigJson.encode(emptyList())).isEmpty())}
    @Test fun malformedOrDuplicateDataIsRejectedInsteadOfBecomingEmpty(){
        val item=TorznabIndexer("same","Indexer","https://indexer.test/api")
        listOf("null","{}","[", "[{}]",Gson().toJson(listOf(item,item))).forEach{raw->assertTrue(runCatching{TorznabConfigJson.decode(raw)}.isFailure)}
    }
    @Test fun oversizedListIsRejected(){assertTrue(runCatching{TorznabConfigJson.encode((1..13).map{TorznabIndexer("id-$it","Indexer","https://indexer.test/api")})}.isFailure)}
}
