package com.ambay.billing.di

import android.content.Context
import com.ambay.billing.BuildConfig
import com.ambay.billing.core.auth.AuthManager
import com.ambay.billing.core.auth.GoogleRecoveryManager
import com.ambay.billing.core.crypto.DeviceKeyManager
import com.ambay.billing.core.database.AmbayDatabase
import com.ambay.billing.core.network.AmbayApiService
import com.ambay.billing.core.network.EnvelopeInterceptor
import com.ambay.billing.core.network.SigningInterceptor
import com.ambay.billing.core.print.BluetoothPrintManager
import com.ambay.billing.core.scan.BeepManager
import com.ambay.billing.core.sync.AmbayRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AmbayDatabase {
        return AmbayDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDeviceKeyManager(): DeviceKeyManager {
        return DeviceKeyManager()
    }

    @Provides
    @Singleton
    fun provideAuthManager(@ApplicationContext context: Context): AuthManager {
        return AuthManager(context)
    }

    @Provides
    @Singleton
    fun provideGoogleRecoveryManager(@ApplicationContext context: Context): GoogleRecoveryManager {
        return GoogleRecoveryManager(context)
    }

    @Provides
    @Singleton
    fun provideBluetoothPrintManager(@ApplicationContext context: Context): BluetoothPrintManager {
        return BluetoothPrintManager(context)
    }

    @Provides
    @Singleton
    fun provideBeepManager(@ApplicationContext context: Context): BeepManager {
        return BeepManager(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        deviceKeyManager: DeviceKeyManager,
        database: AmbayDatabase,
        authManager: AuthManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        val signingInterceptor = SigningInterceptor(
            deviceKeyManager = deviceKeyManager,
            getDeviceKid = {
                // In synchronous interceptor, fetch cached device kid
                "dev_kid_cached"
            }
        )

        val envelopeInterceptor = EnvelopeInterceptor(
            getPinnedServerKeys = { emptyMap() },
            onKeyRevoked = {
                authManager.lock()
            }
        )

        return OkHttpClient.Builder()
            .addInterceptor(signingInterceptor)
            .addInterceptor(envelopeInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideAmbayApiService(okHttpClient: OkHttpClient): AmbayApiService {
        val baseUrl = BuildConfig.DEFAULT_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(AmbayApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAmbayRepository(
        @ApplicationContext context: Context,
        database: AmbayDatabase,
        apiService: AmbayApiService,
        deviceKeyManager: DeviceKeyManager
    ): AmbayRepository {
        return AmbayRepository(context, database, apiService, deviceKeyManager)
    }
}
