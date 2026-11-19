package com.stupidtree.hitax.analytics

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object UsageUploadScheduler {
    private const val WORK = "usage_upload"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<UsageUploadWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInitialDelay(15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK, ExistingWorkPolicy.KEEP, request,
        )
    }
}
