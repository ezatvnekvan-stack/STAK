package app.stak.nativeapp.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
fun youtubeTrailerId(url:String):String?=runCatching{
    val uri=URI(url)
    if(uri.scheme!="https"||uri.userInfo!=null)return@runCatching null
    val host=uri.host?.lowercase() ?: return@runCatching null
    val parts=uri.path.orEmpty().split('/').filter{it.isNotBlank()}
    val id=when(host){
        "youtu.be"->parts.firstOrNull()
        "youtube.com","www.youtube.com","m.youtube.com","www.youtube-nocookie.com"->if(parts.firstOrNull() in listOf("embed","shorts"))parts.getOrNull(1) else if(parts.firstOrNull()=="watch")uri.rawQuery.orEmpty().split('&').firstOrNull{it.substringBefore('=')=="v"}?.substringAfter('=')?.let{URLDecoder.decode(it,"UTF-8")} else null
        else->null
    }
    id?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))}
}.getOrNull()
fun trailerEmbedHtml(id:String,packageName:String):String {
    require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
    require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_.]+")))
    val origin="https://$packageName";val encoded=URLEncoder.encode(origin,"UTF-8")
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body{margin:0;height:100%;background:#000;color:#eee;font:16px sans-serif}#player{width:100%;height:100%;border:0}#error{display:none;position:fixed;inset:0;padding:32px;background:#080808;align-items:center;justify-content:center;text-align:center;pointer-events:none}</style></head><body><iframe id="player" src="https://www.youtube.com/embed/$id?playsinline=1&amp;autoplay=1&amp;enablejsapi=1&amp;origin=$encoded" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe><div id="error"></div><script>
    var ready=false;function fail(message){var el=document.getElementById('error');el.textContent=message;el.style.display='flex';}
    function onYouTubeIframeAPIReady(){new YT.Player('player',{events:{onReady:function(){ready=true;},onError:function(e){ready=true;fail((e.data===101||e.data===150)?'This trailer cannot be embedded by its publisher.':'This trailer could not be played. Use Retry to try again.');}}});}
    setTimeout(function(){if(!ready)fail('The trailer could not load. Check your connection and use Retry.');},20000);
    </script><script src="https://www.youtube.com/iframe_api"></script></body></html>"""
}
