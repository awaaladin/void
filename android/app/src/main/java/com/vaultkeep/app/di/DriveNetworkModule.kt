package com.vaultkeep.app.di

import com.vaultkeep.app.data.remote.DriveApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** No auth interceptor — every Drive call carries its own fresh Authorization header
 * (see DriveAuthManager), since the access token is obtained per-use, not stored. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DriveHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DriveNetworkModule {

    @Provides
    @Singleton
    @DriveHttpClient
    fun provideDriveOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @DriveHttpClient
    fun provideDriveRetrofit(@DriveHttpClient client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveApi(@DriveHttpClient retrofit: Retrofit): DriveApi = retrofit.create()
}
