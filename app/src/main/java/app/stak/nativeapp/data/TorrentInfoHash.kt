package app.stak.nativeapp.data

import java.security.MessageDigest

/** Hash the exact original bencoded info bytes. Never re-encode private metadata. */
fun torrentInfoHash(upload:TorrentUpload):String {
    validateTorrentUpload(upload)
    class Reader(val bytes:ByteArray){
        var at=0;var nodes=0;var infoStart=-1;var infoEnd=-1
        fun fail():Nothing=throw NabFailure("Invalid or unsupported torrent metadata.")
        fun string():ByteArray {
            val start=at
            while(at<bytes.size&&bytes[at].toInt() in 48..57)at++
            if(at==start||at>=bytes.size||bytes[at++].toInt()!=58)fail()
            val text=String(bytes,start,at-start-1,Charsets.US_ASCII)
            if(text.length>8||(text.length>1&&text[0]=='0'))fail()
            val length=text.toIntOrNull()?:fail()
            if(length<0||length>bytes.size-at)fail()
            return bytes.copyOfRange(at,at+length).also{at+=length}
        }
        fun value(depth:Int=0):Any {
            if(depth>48||++nodes>100000||at>=bytes.size)fail()
            return when(bytes[at].toInt()){
                100->{at++;val map=linkedMapOf<String,Any>();while(at<bytes.size&&bytes[at].toInt()!=101){
                    val key=String(string(),Charsets.ISO_8859_1);if(map.containsKey(key))fail()
                    val start=at;val value=value(depth+1);map[key]=value
                    if(depth==0&&key=="info"){infoStart=start;infoEnd=at}
                };if(at>=bytes.size)fail();at++;map}
                108->{at++;val list=mutableListOf<Any>();while(at<bytes.size&&bytes[at].toInt()!=101)list+=value(depth+1);if(at>=bytes.size)fail();at++;list}
                105->{at++;val start=at;while(at<bytes.size&&bytes[at].toInt()!=101)at++;if(at>=bytes.size||at-start>21)fail();val text=String(bytes,start,at-start,Charsets.US_ASCII);at++;if(!text.matches(Regex("0|-?[1-9][0-9]*")))fail();text.toLongOrNull()?:fail()}
                in 48..57->string()
                else->fail()
            }
        }
    }
    val reader=Reader(upload.bytes)
    val root=reader.value() as? Map<*,*> ?: throw NabFailure("Invalid torrent dictionary.")
    val info=root["info"] as? Map<*,*> ?: throw NabFailure("Torrent info is missing.")
    if(reader.at!=upload.bytes.size||reader.infoStart<0)throw NabFailure("Invalid torrent metadata boundaries.")
    val pieces=info["pieces"] as? ByteArray
    if(pieces==null||pieces.isEmpty()||pieces.size%20!=0||(info["piece length"] as? Long ?: 0)<=0||info["name"] !is ByteArray||!(info["length"] is Long||info["files"] is List<*>))
        throw NabFailure("This torrent has no supported v1 cache identifier.")
    return MessageDigest.getInstance("SHA-1").digest(upload.bytes.copyOfRange(reader.infoStart,reader.infoEnd)).joinToString(""){"%02x".format(it)}
}
