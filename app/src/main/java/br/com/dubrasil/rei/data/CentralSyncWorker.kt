package br.com.dubrasil.rei.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CentralSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val dao = ReiDatabase.getInstance(applicationContext).reportDao()
        val client = CentralSyncClient(applicationContext)
        var failed = false
        dao.getPendingSync().forEach { entity ->
            val attempt = System.currentTimeMillis()
            client.send(entity)
                .onSuccess { dao.updateSyncStatus(entity.dbId, ReportEntity.SYNC_SYNCED, attempt, null) }
                .onFailure { error ->
                    failed = true
                    dao.updateSyncStatus(
                        entity.dbId,
                        ReportEntity.SYNC_ERROR,
                        attempt,
                        error.message?.take(500)
                    )
                }
        }
        if (!failed) {
            client.fetchCompletedReports()
                .onSuccess { remoteReports ->
                    if (remoteReports.isNotEmpty()) dao.upsertAll(remoteReports)
                }
                .onFailure { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }
}

object SyncScheduler {
    private const val UNIQUE_WORK = "rei_central_sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<CentralSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
