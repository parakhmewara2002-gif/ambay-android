package com.ambay.billing

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ambay.billing.data.local.SecurePrefs
import com.ambay.billing.data.worker.SyncWorker
import com.ambay.billing.data.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AmbayApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var securePrefs: SecurePrefs

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                securePrefs.setLastBackgroundTs(System.currentTimeMillis())
            }
        })
        scheduleBackgroundWorkers()
    }

    private fun scheduleBackgroundWorkers() {
        val wm = WorkManager.getInstance(this)
        val netConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncReq = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(netConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniquePeriodicWork("ambay_periodic_sync", ExistingPeriodicWorkPolicy.KEEP, syncReq)

        val updateReq = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(netConstraints)
            .build()
        wm.enqueueUniquePeriodicWork("ambay_daily_update_check", ExistingPeriodicWorkPolicy.KEEP, updateReq)
    }
}
