package com.ambay.billing

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class AmbayBillingApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupBouncyCastle()
    }

    private fun setupBouncyCastle() {
        // Insert Bouncy Castle provider for Ed25519 support if not present
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing == null) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }
}
