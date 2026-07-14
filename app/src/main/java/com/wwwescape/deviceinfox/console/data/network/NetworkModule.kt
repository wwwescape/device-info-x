package com.wwwescape.deviceinfox.console.data.network

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Body logging is gated on [ApplicationInfo.FLAG_DEBUGGABLE], not a build-variant split —
     * same debug-gating approach Phase 2 used for its temporary debug trigger. Bodies can
     * contain message text/tokens, so this must never be on in a release build. */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(@ApplicationContext context: Context): HttpLoggingInterceptor {
        val isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return HttpLoggingInterceptor().apply {
            level = if (isDebuggable) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        baseUrlInterceptor: BaseUrlInterceptor,
        authHeaderInterceptor: AuthHeaderInterceptor,
        authAuthenticator: AuthAuthenticator,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrlInterceptor)
        .addInterceptor(authHeaderInterceptor)
        .authenticator(authAuthenticator)
        .addInterceptor(loggingInterceptor)
        // Previously unset — OkHttp's connect/read/write timeouts default to 10s each, which is
        // fine for small JSON calls but callTimeout (the only one that bounds the *whole* call,
        // including a slow multipart upload/download) defaults to 0 (no limit). A stalled
        // media upload/download would then hang forever with no exception ever thrown, which is
        // exactly what silently broke image attach/import: consoleApiCall only translates
        // Retrofit's HttpException, so a call that never completes at all never even reaches
        // that catch block — it just never returns. 60s comfortably covers a large voice note
        // or photo on a slow connection while still failing visibly instead of hanging.
        .connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(ConsoleServerConfig.PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun providePairingApi(retrofit: Retrofit): PairingApi = retrofit.create(PairingApi::class.java)

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi = retrofit.create(UsersApi::class.java)

    @Provides
    @Singleton
    fun provideMessagesApi(retrofit: Retrofit): MessagesApi = retrofit.create(MessagesApi::class.java)

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi = retrofit.create(MediaApi::class.java)

    @Provides
    @Singleton
    fun provideCalendarApi(retrofit: Retrofit): CalendarApi = retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun providePeriodApi(retrofit: Retrofit): PeriodApi = retrofit.create(PeriodApi::class.java)

    @Provides
    @Singleton
    fun provideLockerApi(retrofit: Retrofit): LockerApi = retrofit.create(LockerApi::class.java)

    @Provides
    @Singleton
    fun provideNotepadApi(retrofit: Retrofit): NotepadApi = retrofit.create(NotepadApi::class.java)

    @Provides
    @Singleton
    fun provideScheduledMessagesApi(retrofit: Retrofit): ScheduledMessagesApi =
        retrofit.create(ScheduledMessagesApi::class.java)

    @Provides
    @Singleton
    fun provideDevicesApi(retrofit: Retrofit): DevicesApi = retrofit.create(DevicesApi::class.java)

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideReleasesApi(retrofit: Retrofit): ReleasesApi = retrofit.create(ReleasesApi::class.java)

    @Provides
    @Singleton
    fun provideLocationApi(retrofit: Retrofit): LocationApi = retrofit.create(LocationApi::class.java)

    @Provides
    @Singleton
    fun provideTurnApi(retrofit: Retrofit): TurnApi = retrofit.create(TurnApi::class.java)

    @Provides
    @Singleton
    fun provideCallLogApi(retrofit: Retrofit): CallLogApi = retrofit.create(CallLogApi::class.java)
}
