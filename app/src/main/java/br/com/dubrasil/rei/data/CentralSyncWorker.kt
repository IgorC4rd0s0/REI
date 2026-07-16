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
        val auth = AuthStore(applicationContext)
        val attempt = System.currentTimeMillis()
        auth.beginSyncAttempt(attempt)
        var sendFailed = false
        var retryRequired = false
        var lastError: String? = null
        client.fetchSchemaOverrides().onFailure { error ->
            lastError = error.message ?: "Não foi possível atualizar os itens dos relatórios."
            retryRequired = true
        }
        dao.getPendingSync().forEach { entity ->
            client.send(entity)
                .onSuccess { dao.updateSyncStatus(entity.dbId, ReportEntity.SYNC_SYNCED, attempt, null) }
                .onFailure { error ->
                    sendFailed = true
                    lastError = error.message ?: "Falha ao sincronizar ${entity.client}."
                    retryRequired = retryRequired || error !is ServerReportException
                    dao.updateSyncStatus(
                        entity.dbId,
                        ReportEntity.SYNC_ERROR,
                        attempt,
                        error.message?.take(500)
                    )
                    if (error is ServerReportException) {
                        ReiNotifier.notify(
                            applicationContext,
                            entity.reportId.hashCode(),
                            "Relatório não sincronizado",
                            error.message ?: "O servidor rejeitou a alteração do relatório."
                        )
                    }
                }
        }
        if (!sendFailed) {
            val existingIds = dao.getCompleted().map { it.reportId }.toSet()
            client.fetchCompletedReports()
                .onSuccess { remoteReports ->
                    if (remoteReports.isNotEmpty()) {
                        dao.upsertAll(remoteReports)
                        remoteReports.filterNot { it.reportId in existingIds }.forEach { report ->
                            val stage = runCatching {
                                org.json.JSONObject(report.payloadJson).optJSONObject("fields")?.optString("_stage").orEmpty()
                            }.getOrDefault("")
                            when (stage) {
                                "levantamento_pendente" -> ReiNotifier.notify(
                                    applicationContext,
                                    report.reportId.hashCode(),
                                    "Novo levantamento recebido",
                                    "${report.client.ifBlank { "Cliente não informado" }} está aguardando preenchimento do levantamento."
                                )
                                "rei_pendente" -> ReiNotifier.notify(
                                    applicationContext,
                                    report.reportId.hashCode(),
                                    "R.E.I. liberado para preenchimento",
                                    "${report.client.ifBlank { "Cliente não informado" }} está aguardando o relatório R.E.I."
                                )
                            }
                        }
                    }
                }
                .onFailure { error ->
                    lastError = error.message ?: "Não foi possível consultar o servidor."
                    retryRequired = true
                }
        }
        val pendingCount = dao.countPendingSync()
        client.sendHeartbeat(pendingCount, lastError).onFailure { error ->
            if (lastError == null) lastError = error.message ?: "Falha ao enviar diagnóstico ao servidor."
            retryRequired = retryRequired || error !is ServerReportException
        }
        auth.finishSyncAttempt(lastError)
        return if (retryRequired) Result.retry() else Result.success()
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
