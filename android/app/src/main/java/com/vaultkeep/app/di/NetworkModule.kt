package com.vaultkeep.app.di

import com.vaultkeep.app.BuildConfig
import com.vaultkeep.app.data.local.CloudAuthPreferences
import com.vaultkeep.app.data.remote.RefreshRequest
import com.vaultkeep.app.data.remote.VoidApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The client used for calls to our own API — carries the Authorization header and auto-refreshes on 401. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiHttpClient

/** The client used for presigned-URL PUTs straight to R2/B2 — must never carry our API's Authorization header. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UploadHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Attaches the access token to every request, and on a single 401 tries
     * a synchronous refresh (SimpleJWT's refresh endpoint) before retrying
     * once. No retry loop beyond that — a second 401 means the refresh
     * token itself is dead and the caller should prompt sign-in again.
     */
    @Provides
    @Singleton
    @ApiHttpClient
    fun provideOkHttpClient(authPreferences: CloudAuthPreferences, json: Json): OkHttpClient {
        val authInterceptor = okhttp3.Interceptor { chain ->
            val token = authPreferences.accessToken()
            val request = if (token != null) {
                chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            var response: Response = chain.proceed(request)

            if (response.code == 401 && authPreferences.refreshToken() != null) {
                response.close()
                val newAccess = runCatching { refreshAccessToken(authPreferences, json) }.getOrNull()
                if (newAccess != null) {
                    authPreferences.saveAccessToken(newAccess)
                    val retried = chain.request().newBuilder().addHeader("Authorization", "Bearer $newAccess").build()
                    response = chain.proceed(retried)
                }
            }
            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS) // uploads can be slow on mobile networks
            .build()
    }

    /** Plain, un-intercepted client for presigned-URL PUTs — those go straight to R2/B2, never our API, and must never carry our Authorization header. */
    @Provides
    @Singleton
    @UploadHttpClient
    fun provideUploadHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun refreshAccessToken(authPreferences: CloudAuthPreferences, json: Json): String? {
        val refresh = authPreferences.refreshToken() ?: return null
        val client = OkHttpClient()
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refresh))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${BuildConfig.VOID_CLOUD_BASE_URL}api/accounts/login/refresh/")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            return runCatching {
                json.decodeFromString(com.vaultkeep.app.data.remote.RefreshResponse.serializer(), text).access
            }.getOrNull()
        }
    }

    @Provides
    @Singleton
    fun provideRetrofit(@ApiHttpClient client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.VOID_CLOUD_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideVoidApi(retrofit: Retrofit): VoidApi = retrofit.create()
}
