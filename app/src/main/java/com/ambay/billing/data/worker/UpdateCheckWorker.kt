package com.ambay.billing.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ambay.billing.BuildConfig
import com.ambay.billing.data.remote.GitHubUpdateClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateClient: GitHubUpdateClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val latest = updateClient.checkLatestRelease()
            if (latest != null && GitHubUpdateClient.isUpdateAvailable(latest.tagName, BuildConfig.VERSION_CODE)) {
                applicationContext.getSharedPreferences("ambay_update_badge", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("has_update", true)
                    .putString("latest_tag", latest.tagName)
                    .apply()
            }
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
