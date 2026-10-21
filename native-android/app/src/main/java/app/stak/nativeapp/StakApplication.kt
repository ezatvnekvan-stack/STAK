package app.stak.nativeapp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

fun allowsPrivateArtworkHeader(request: HttpUrl, serverAddress: String): Boolean {
    val server = serverAddress.trim().trimEnd('/').toHttpUrlOrNull() ?: return false
    return request.scheme == server.scheme &&
        request.host == server.host &&
        request.port == server.port &&
        request.encodedPath.startsWith(server.encodedPath.trimEnd('/') + "/")
}

class StakApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val credentials = Credentials(this)
        val client =
            OkHttpClient.Builder()
                .addNetworkInterceptor { chain ->
                    val original = chain.request()
                    // Public artwork may redirect; a private Jellyfin header stays on its
                    // configured server.
                    val request =
                        if (
                            original.header("X-Emby-Token") != null &&
                                !allowsPrivateArtworkHeader(
                                    original.url,
                                    credentials.get("jellyfin_url"),
                                )
                        )
                            original
                                .newBuilder()
                                .removeHeader("X-Emby-Token")
                                .removeHeader("Authorization")
                                .build()
                        else original
                    chain.proceed(request)
                }
                .build()
        return ImageLoader.Builder(this).okHttpClient(client).build()
    }
}
