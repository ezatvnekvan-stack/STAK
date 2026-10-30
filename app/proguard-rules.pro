# Keep Compose and data classes
-keep class app.stak.nativeapp.data.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel { public <init>(...); }

# Retrofit / OkHttp
-keepattributes Signature
# Gson's documented rules also protect existing reflected settings outside Torznab.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Tink / crypto
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**