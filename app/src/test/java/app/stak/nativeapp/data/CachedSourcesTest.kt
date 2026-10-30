package app.stak.nativeapp.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class CachedSourcesTest {
    private fun str(text:String)=text.toByteArray().let{it.size.toString().toByteArray()+byteArrayOf(58)+it}
    private fun torrent(info:ByteArray,announce:String="https://tracker.invalid/private-key")=TorrentUpload("private.torrent","d8:announce".toByteArray()+str(announce)+"4:info".toByteArray()+info+"e".toByteArray())
    private val info="d6:lengthi1e4:name1:x12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste".toByteArray()
    private fun hash(n:Int)=n.toString(16).padStart(40,'0')
    private fun release(n:Int)=SourceRelease("Release $n",infoHash=hash(n))
    private fun unknown(n:Int)=SourceRelease("Release $n",infoHash=null,torznabIndexerId="tl",torrentFileUrl="https://indexer.invalid/$n.torrent",sourceToken="$n")
    private fun prepared(s:SourceRelease,n:Int)=PreparedSource(s.copy(infoHash=hash(n)),NabMaterial.Magnet(parseMagnetInput(hash(n))))
    @Test fun rawInfoBytesProduceTheExactHash(){assertEquals(MessageDigest.getInstance("SHA-1").digest(info).joinToString(""){"%02x".format(it)},torrentInfoHash(torrent(info)))}
    @Test fun changingPrivatePasskeyDoesNotChangeInfoHash(){assertEquals(torrentInfoHash(torrent(info,"https://tracker.invalid/first")),torrentInfoHash(torrent(info,"https://tracker.invalid/second")))}
    @Test fun infoIsNotReencodedOrSorted(){val other="d4:name1:x6:lengthi1e12:piece lengthi16384e6:pieces20:abcdefghijklmnopqrste".toByteArray();assertNotEquals(torrentInfoHash(torrent(info)),torrentInfoHash(torrent(other)))}
    @Test fun duplicateInfoIsRejected(){val bad=TorrentUpload("x.torrent","d4:info".toByteArray()+info+"4:info".toByteArray()+info+"e".toByteArray());assertTrue(runCatching{torrentInfoHash(bad)}.isFailure)}
    @Test fun malformedLengthsAndTrailingDataAreRejected(){listOf("d4:info999999999999999999:xee","d4:infoi03ee","d4:infodeeJUNKe").forEach{assertTrue(runCatching{torrentInfoHash(TorrentUpload("x.torrent",it.toByteArray()))}.isFailure)}}
    @Test fun deeplyNestedMetadataIsRejected(){val bad="d4:info"+"l".repeat(60)+"e".repeat(61);assertTrue(runCatching{torrentInfoHash(TorrentUpload("x.torrent",bad.toByteArray()))}.isFailure)}
    @Test fun v2OnlyMetadataStaysUnverified(){assertTrue(runCatching{torrentInfoHash(torrent("d12:meta versioni2e4:name1:xe".toByteArray()))}.isFailure)}
    @Test fun metadataMustBeSmallAndTorrentShaped(){assertTrue(runCatching{torrentInfoHash(TorrentUpload("x.torrent",ByteArray(MAX_TORRENT_BYTES+1)))}.isFailure)}
    @Test fun knownHashesAreBatchedAndPublishedProgressively()=runBlocking{
        val batches=mutableListOf<Int>();val progress=mutableListOf<Int>()
        val session=CachedSourceSession("key",{hashes,k->assertEquals("key",k);batches+=hashes.size;hashes.toSet()+"f".repeat(40)})
        session.accept((1..81).map(::release));session.known{progress+=it.releases.size}
        assertEquals(listOf(80,1),batches);assertEquals(80,progress.first());assertEquals(81,session.snapshot().releases.size)
    }
    @Test fun metadataIsBoundedAndMoreDoesNotRepeatEarlierRequests()=runBlocking{
        val fetched=mutableListOf<String>();val session=CachedSourceSession("key",{hashes,_->hashes.toSet()},{s,_->fetched+=s.sourceToken!!;prepared(s,s.sourceToken!!.toInt())})
        session.accept((1..10).map(::unknown));session.more{}
        assertEquals(8,fetched.size);assertEquals(2,session.snapshot().remaining);session.more{}
        assertEquals(10,fetched.distinct().size);assertEquals(10,session.snapshot().releases.size)
    }
    @Test fun missesFailuresAndPlaybackLinksNeverBecomeCached()=runBlocking{
        val session=CachedSourceSession("key",{_,_->emptySet()},{_,_->throw NabFailure("Unavailable")})
        session.accept(listOf(release(1),unknown(2),SourceRelease("Playback",infoHash=null,streamUrl="https://media.invalid/movie.mkv")))
        session.known{};session.more{};assertTrue(session.snapshot().releases.isEmpty());assertEquals(1,session.snapshot().metadataFailures)
    }
    @Test fun duplicateHashesPreferPrivateMetadataAndAreCheckedOnce()=runBlocking{
        var requests=0;val plain=release(1);val private=plain.copy(torznabIndexerId="tl",torrentFileUrl="https://indexer.invalid/1.torrent")
        val session=CachedSourceSession("key",{hashes,_->requests++;hashes.toSet()});session.accept(listOf(plain));session.known{};session.accept(listOf(private));session.known{}
        assertEquals(1,requests);assertEquals(private,session.snapshot().releases.single())
    }
    @Test fun metadataChecksKeepQualityFirst()=runBlocking{
        val seen=mutableListOf<String>();val session=CachedSourceSession("key",{h,_->h.toSet()},{s,_->seen+=s.name;prepared(s,1)})
        session.accept(listOf(unknown(1).copy(name="1080p WEB-DL",resolution="1080p"),unknown(2).copy(name="2160p REMUX",resolution="2160p",remux=true)));session.more{}
        assertEquals("2160p REMUX",seen.first())
    }
    @Test fun failedCacheChecksAreNeverTreatedAsMisses()=runBlocking{
        var fail=true;val session=CachedSourceSession("key",{h,_->if(fail)throw java.io.IOException("Offline")else h.toSet()});session.accept(listOf(release(1)))
        assertTrue(runCatching{session.known{}}.isFailure);assertTrue(session.snapshot().hashes.isEmpty());fail=false;session.known{};assertEquals(1,session.snapshot().releases.size)
    }
    @Test fun expiredCacheNeverCallsTheMutation()=runBlocking{
        var adds=0;val result=runCatching{addCachedSource(release(1),"key",{s,_->prepared(s,1)},{_,_->emptySet()},magnetAdd={_,_->adds++;"id"},currentKey={"key"})}
        assertTrue(result.exceptionOrNull()is NabFailure);assertEquals(0,adds)
    }
    @Test fun accountSwitchDuringVerificationNeverAdds()=runBlocking{
        var account="key";var adds=0;val result=runCatching{addCachedSource(release(1),"key",{s,_->prepared(s,1)},{h,_->account="other";h.toSet()},magnetAdd={_,_->adds++;"id"},currentKey={account})}
        assertTrue(result.isFailure);assertEquals(0,adds)
    }
    @Test fun verifiedFileKeepsItsOriginalPrivateMetadata()=runBlocking{
        val upload=torrent(info);val hash=torrentInfoHash(upload);val source=release(1).copy(infoHash=hash);var sent:TorrentUpload?=null
        val result=addCachedSource(source,"key",{s,_->PreparedSource(s,NabMaterial.Torrent(upload))},{h,_->h.toSet()},torrentAdd={_,f->sent=f;"17"},magnetAdd={_,_->error("No magnet fallback")},currentKey={"key"})
        assertEquals("17",result);assertSame(upload,sent)
    }
    @Test fun changedMetadataNeverCallsTheMutation()=runBlocking{
        var adds=0;assertTrue(runCatching{addCachedSource(release(1),"key",{s,_->prepared(s,2)},{h,_->h.toSet()},magnetAdd={_,_->adds++;"id"},currentKey={"key"})}.isFailure);assertEquals(0,adds)
    }
    @Test fun sourcesWithoutAHashCannotBeAdded()=runBlocking{
        var prepared=false;assertTrue(runCatching{addCachedSource(unknown(1),"key",{s,_->prepared=true;prepared(s,1)},currentKey={"key"})}.isFailure);assertFalse(prepared)
    }
}
