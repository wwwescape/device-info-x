package com.wwwescape.deviceinfox.console.data.location

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/** Marks the [Retrofit] instance pointed at Google's fixed `maps.googleapis.com` host — kept
 * distinct from the console's own (`NetworkModule`'s unqualified [Retrofit]) so nothing
 * accidentally sends Google a bearer token meant for the user's self-hosted server, or vice
 * versa. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DirectionsRetrofit

@Module
@InstallIn(SingletonComponent::class)
object DirectionsModule {

    @Provides
    @Singleton
    @DirectionsRetrofit
    fun provideDirectionsRetrofit(json: Json): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideDirectionsApi(@DirectionsRetrofit retrofit: Retrofit): DirectionsApi =
        retrofit.create(DirectionsApi::class.java)
}
