package app.stak.nativeapp.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    @Volatile
    var baseUrl: String = SettingsStore.DEFAULT_SERVER
        private set

    @Volatile
    private var cachedUrl: String = ""

    @Volatile
    private var cachedService: ApiService? = null

    fun configure(url: String) {
        val normalized = normalize(url)
        if (normalized != baseUrl) {
            baseUrl = normalized
            cachedService = null
            cachedUrl = ""
        }
    }

    private fun normalize(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        val withApi = if (trimmed.endsWith("/api", ignoreCase = true)) trimmed else "$trimmed/api"
        return withApi + "/"
    }

    val service: ApiService
        get() {
            val current = baseUrl
            val existing = cachedService
            if (existing != null && cachedUrl == current) return existing
            return synchronized(this) {
                if (cachedService != null && cachedUrl == current) {
                    cachedService!!
                } else {
                    build(current).also {
                        cachedService = it
                        cachedUrl = current
                    }
                }
            }
        }

    fun recreate(): ApiService {
        cachedService = null
        return service
    }

    private fun build(url: String): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
