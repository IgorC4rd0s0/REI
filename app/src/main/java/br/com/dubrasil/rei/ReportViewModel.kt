package br.com.dubrasil.rei

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.dubrasil.rei.data.ReportRepository
import br.com.dubrasil.rei.data.ReiReminderScheduler
import br.com.dubrasil.rei.data.SchemaStore
import br.com.dubrasil.rei.data.DeviceSyncStatus
import br.com.dubrasil.rei.data.SyncDiagnostic
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportAttachment
import br.com.dubrasil.rei.model.ImplementationSummary
import br.com.dubrasil.rei.model.ReportSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ReportViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReportRepository(application)
    var report by mutableStateOf(repository.load())
        private set
    var history by mutableStateOf(repository.loadHistory())
        private set
    var schemaVersion by mutableStateOf(0)
        private set
    var serverMessage by mutableStateOf<String?>(null)
        private set
    var syncDiagnostic by mutableStateOf(repository.loadSyncDiagnostic())
        private set
    var deviceStatuses by mutableStateOf<List<DeviceSyncStatus>>(emptyList())
        private set
    var isSyncing by mutableStateOf(false)
        private set

    init {
        SchemaStore(application).applyCached()
    }

    fun setField(key: String, value: String) = update(report.copy(fields = report.fields + (key to value)))

    fun toggle(item: String) {
        val updated = if (item in report.checks) report.checks - item else report.checks + item
        update(report.copy(checks = updated))
    }

    fun setDeliveryStatus(value: String) = update(report.copy(deliveryStatus = value))
    fun setRating(value: String) = update(report.copy(rating = value))

    fun addAttachments(items: List<ReportAttachment>) {
        val existing = report.attachments.map { it.uri }.toSet()
        update(report.copy(attachments = report.attachments + items.filterNot { it.uri in existing }))
    }

    fun removeAttachment(uri: String) =
        update(report.copy(attachments = report.attachments.filterNot { it.uri == uri }))

    fun clear() {
        report = ReportData()
        repository.clear()
    }

    fun startNewReport(ownerUsername: String = "") {
        report = ReportData(fields = buildMap {
            put("_id", UUID.randomUUID().toString())
            if (ownerUsername.isNotBlank()) put("_ownerUsername", ownerUsername)
        })
        repository.clear()
        repository.save(report)
    }

    fun startNewSurvey(ownerUsername: String = "", ownerName: String = ""): String {
        val id = UUID.randomUUID().toString()
        val data = ReportData(fields = buildMap {
            put("_id", id)
            put("_stage", "levantamento_pendente")
            if (ownerUsername.isNotBlank()) {
                put("_ownerUsername", ownerUsername)
                put("_createdBy", ownerUsername)
                put("_assignedImplantadorUsername", ownerUsername)
            }
            if (ownerName.isNotBlank()) {
                put("_assignedImplantadorName", ownerName)
                put("analistaLevantamento", ownerName)
            }
        })
        val summary = ImplementationSummary(
            id = id,
            client = "Cliente não informado",
            consultant = data.field("consultor"),
            completedAt = System.currentTimeMillis(),
            deliveryStatus = data.deliveryStatus,
            checkedItems = 0,
            report = data
        )
        history = (history + summary).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(summary)
        report = data
        repository.save(report)
        return id
    }

    fun editCompletedReport(id: String, ownerUsername: String = "") {
        val completed = history.firstOrNull { it.id == id } ?: return
        report = completed.report.copy(fields = completed.report.fields + buildMap {
            put("_id", id)
            put("_stage", "rei")
            if (ownerUsername.isNotBlank()) put("_ownerUsername", ownerUsername)
        })
        repository.clear()
        repository.save(report)
    }

    fun createSurveyClient(fields: Map<String, String>, supervisorUsername: String = "") {
        val id = UUID.randomUUID().toString()
        val data = ReportData(fields = fields + mapOf(
            "_id" to id,
            "_stage" to "levantamento_pendente",
            "_createdBy" to supervisorUsername
        ))
        val summary = ImplementationSummary(
            id = id,
            client = data.field("cliente").ifBlank { data.field("empresa").ifBlank { "Cliente não informado" } },
            consultant = data.field("consultor"),
            completedAt = System.currentTimeMillis(),
            deliveryStatus = data.deliveryStatus,
            checkedItems = 0,
            report = data
        )
        history = (history + summary).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(summary)
    }

    fun updateSurveyClient(id: String, fields: Map<String, String>, supervisorUsername: String = "") {
        val item = history.firstOrNull { it.id == id } ?: return
        val data = item.report.copy(fields = item.report.fields + fields + mapOf(
            "_id" to id,
            "_stage" to "levantamento_pendente",
            "_createdBy" to item.report.field("_createdBy").ifBlank { supervisorUsername }
        ))
        val updated = item.copy(
            client = data.field("cliente").ifBlank { data.field("empresa").ifBlank { "Cliente não informado" } },
            consultant = data.field("consultor"),
            deliveryStatus = data.deliveryStatus,
            checkedItems = deliveryChecklistCount(data),
            report = data,
            syncStatus = "PENDING"
        )
        history = (history.filterNot { it.id == id } + updated).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(updated)
    }

    fun openSurvey(id: String) {
        val item = history.firstOrNull { it.id == id } ?: return
        report = item.report.copy(fields = item.report.fields + ("_id" to id))
    }

    fun saveSurveyDraft() {
        saveCurrentStage("levantamento_pendente")
    }

    fun completeSurvey() {
        saveCurrentStage("rei_pendente")
        report = ReportData()
        repository.clear()
    }

    private fun saveCurrentStage(stage: String) {
        val id = report.field("_id").ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val data = report.copy(fields = report.fields + buildMap {
            put("_id", id)
            put("_stage", stage)
            put("cliente", report.field("cliente").ifBlank { report.field("empresa") })
            if (stage == "rei_pendente") put("_surveyCompletedAt", now.toString())
        })
        val existing = history.firstOrNull { it.id == id }
        val summary = ImplementationSummary(
            id = id,
            client = data.field("cliente").ifBlank { "Cliente não informado" },
            consultant = data.field("consultor"),
            completedAt = if (stage == "rei_pendente") now else existing?.completedAt ?: now,
            deliveryStatus = data.deliveryStatus,
            checkedItems = deliveryChecklistCount(data),
            report = data
        )
        history = (history.filterNot { it.id == id } + summary).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(summary)
        report = data
        repository.save(report)
        if (stage == "levantamento_pendente") {
            ReiReminderScheduler.scheduleSurveyReminder(
                getApplication(),
                id,
                data.field("cliente").ifBlank { data.field("empresa") },
                data.field("_surveyScheduledAt")
            )
        }
    }

    fun saveSupervisorEvaluation(id: String, supervisorName: String, score: String, rating: String, supervisionChecks: Set<String>) {
        val item = history.firstOrNull { it.id == id } ?: return
        val supervisionKeys = ReportSchema.supervisionChecklistItems().toSet()
        val updatedReport = item.report.copy(
            fields = item.report.fields + buildMap {
                put("_id", id)
                if (supervisorName.isNotBlank()) put("_supervisorName", supervisorName)
                put("_supervisionScore", score.trim())
                put("_supervisionReviewedAt", System.currentTimeMillis().toString())
            },
            checks = (item.report.checks - supervisionKeys) + supervisionChecks.filter { it in supervisionKeys },
            rating = rating.trim()
        )
        val updated = item.copy(
            checkedItems = deliveryChecklistCount(updatedReport),
            report = updatedReport,
            syncStatus = "PENDING"
        )
        history = (history.filterNot { it.id == id } + updated).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(updated)
    }

    fun archiveCurrentReport() {
        val id = report.field("_id").ifBlank { UUID.randomUUID().toString() }
        if (report.field("_id").isBlank()) update(report.copy(fields = report.fields + ("_id" to id)))
        val existing = history.firstOrNull { it.id == id }
        val summary = ImplementationSummary(
            id = id,
            client = report.field("cliente").ifBlank { "Cliente não informado" },
            consultant = report.field("consultor"),
            completedAt = existing?.completedAt ?: System.currentTimeMillis(),
            deliveryStatus = report.deliveryStatus,
            checkedItems = deliveryChecklistCount(report),
            report = report
        )
        history = (history.filterNot { it.id == id } + summary).sortedByDescending { it.completedAt }
        repository.upsertHistoryItem(summary)
        report = ReportData()
        repository.clear()
    }

    fun refreshFromServer() {
        if (hasUnmeteredNetwork()) {
            runSynchronization(showSuccess = false)
        } else {
            viewModelScope.launch {
                val local = withContext(Dispatchers.IO) {
                    repository.loadHistory() to repository.loadSyncDiagnostic()
                }
                history = local.first
                syncDiagnostic = local.second
            }
        }
    }

    fun synchronizeNow() {
        runSynchronization(showSuccess = true)
    }

    private fun runSynchronization(showSuccess: Boolean) {
        if (isSyncing) return
        isSyncing = true
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val run = repository.syncNow()
                    Triple(repository.loadHistory(), run, repository.loadDeviceStatuses())
                }
                history = result.first
                syncDiagnostic = result.second.diagnostic
                deviceStatuses = result.third
                serverMessage = result.second.attemptError ?: if (showSuccess) "Sincronização concluída." else null
                SchemaStore(getApplication()).applyCached()
                schemaVersion++
            } catch (error: Exception) {
                syncDiagnostic = withContext(Dispatchers.IO) { repository.loadSyncDiagnostic() }
                serverMessage = error.message ?: "Não foi possível concluir a sincronização."
            } finally {
                isSyncing = false
            }
        }
    }

    private fun hasUnmeteredNetwork(): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun consumeServerMessage() {
        serverMessage = null
    }

    private fun deliveryChecklistCount(data: ReportData) =
        data.checks.count { it in ReportSchema.allChecklistItems() }

    private fun update(value: ReportData) {
        report = value
        repository.save(value)
    }
}
