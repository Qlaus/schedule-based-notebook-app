package io.github.qlaus.calnotes.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.qlaus.calnotes.BuildConfig
import io.github.qlaus.calnotes.auth.AuthManager
import io.github.qlaus.calnotes.auth.CurrentAccount
import io.github.qlaus.calnotes.data.local.AppDatabase
import io.github.qlaus.calnotes.data.remote.AuthInterceptor
import io.github.qlaus.calnotes.data.remote.calendar.CalendarApi
import io.github.qlaus.calnotes.data.repo.ZoneProvider
import io.github.qlaus.calnotes.diagnostics.CrashReporter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(@ApplicationContext context: Context): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, e -> CrashReporter.recordNonFatal(context, e) },
        )

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun zoneProvider(): ZoneProvider = ZoneProvider { ZoneId.systemDefault() }

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun okHttp(auth: AuthManager): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(auth))
        .apply {
            if (BuildConfig.DEBUG) {
                // Request lines and status codes only: bodies contain personal data, tokens are redacted.
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }
        .build()

    @Provides
    @Singleton
    fun calendarApi(client: OkHttpClient, json: Json): CalendarApi = Retrofit.Builder()
        .baseUrl(CalendarApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CalendarApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun currentAccount(impl: AuthManager): CurrentAccount
}
