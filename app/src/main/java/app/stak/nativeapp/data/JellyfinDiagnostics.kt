package app.stak.nativeapp.data

import java.net.*
import javax.net.ssl.SSLException

data class ContinueFeed(val items:List<MediaItem>,val issue:String?=null)

/** UserData is controlled by EnableUserData; RunTimeTicks/PresentationUniqueKey are DTO properties, not ItemFields. */
fun jellyfinQuery(params:Map<String,String>):Map<String,String> {
    val fields=params.entries.firstOrNull{it.key.equals("Fields",true)} ?: return params
    val requested=fields.value.split(',').map{it.trim()}.filter{it.isNotEmpty()}
    val filtered=requested.filterNot{it.equals("UserData",true)||it.equals("RunTimeTicks",true)||it.equals("PresentationUniqueKey",true)}.distinct()
    return params-fields.key+mapOf("Fields" to filtered.joinToString(","))+
        if(requested.any{it.equals("UserData",true)}&&params.keys.none{it.equals("EnableUserData",true)})mapOf("EnableUserData" to "true")else emptyMap()
}
/** Never expose an exception's raw URL, credentials, response body or internal stack in product text. */
fun jellyfinFailureMessage(error:Throwable):String=when(error){
    is JellyfinHttpFailure->when(error.status){
        400->"Jellyfin rejected the request (400). Retry or check server compatibility."
        401->"Jellyfin session expired (401). Reconnect in Settings."
        403->"Jellyfin denied access (403). Check this user’s permissions."
        404->"Jellyfin endpoint or item was not found (404). Check the server base URL."
        in 300..399->"Jellyfin redirected the request. Configure its final server URL in Settings."
        in 500..599->"Jellyfin server error (${error.status}). Retry when the server is ready."
        else->"Jellyfin request failed (${error.status}). Retry."
    }
    is SocketTimeoutException->"Jellyfin timed out. Check Wi-Fi/VPN or remote server access, then retry."
    is UnknownHostException->"Jellyfin address could not be resolved. Check the server URL and DNS/VPN."
    is SSLException->"Jellyfin HTTPS verification failed. Check the server certificate and device clock."
    is ConnectException,is NoRouteToHostException->"Jellyfin is unreachable. A local server needs home Wi-Fi or a connected VPN."
    else->"Jellyfin could not update. Check the connection and retry; saved tracking is kept."
}
