package br.com.dubrasil.rei

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.dubrasil.rei.data.ReportRepository
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
        repository.saveHistory(history)
    }

    fun updateSurveyClient(id: String, fields: Map<String, String>, supervisorUsername: String = "") {
        val item = history.firstOrNull { it.id == id } ?: return
        val data = item.report.copy(fields = item.report.fields + fields + mapOf(
            "_id" to id,
            "_stage" to "levantamento_pendente",
            "_createdBy" to item.report.field("_createdBy").ifBlank { supervisorUsername }
        ))
        val updated = item.copy(
            client = data.field("cliente").ifBlank { data.field("empresa").ifBlank { "Cliente nÃ£o informado" } },
            consultant = data.field("consultor"),
            deliveryStatus = data.deliveryStatus,
            checkedItems = deliveryChecklistCount(data),
            report = data
        )
        history = (history.filterNot { it.id == id } + updated).sortedByDescending { it.completedAt }
        repository.saveHistory(history)
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
        val data = report.copy(fields = report.fields + mapOf(
            "_id" to id,
            "_stage" to stage,
            "cliente" to report.field("cliente").ifBlank { report.field("empresa") }
        ))
        val existing = history.firstOrNull { it.id == id }
        val summary = ImplementationSummary(
            id = id,
            client = data.field("cliente").ifBlank { "Cliente não informado" },
            consultant = data.field("consultor"),
            completedAt = existing?.completedAt ?: System.currentTimeMillis(),
            deliveryStatus = data.deliveryStatus,
            checkedItems = deliveryChecklistCount(data),
            report = data
        )
        history = (history.filterNot { it.id == id } + summary).sortedByDescending { it.completedAt }
        repository.saveHistory(history)
        report = data
        repository.save(report)
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
            report = updatedReport
        )
        history = (history.filterNot { it.id == id } + updated).sortedByDescending { it.completedAt }
        repository.saveHistory(history)
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
        repository.saveHistory(history)
        report = ReportData()
        repository.clear()
    }

    fun refreshFromServer() {
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) {
                repository.syncNow()
                repository.loadHistory()
            }
            history = latest
        }
    }

    private fun deliveryChecklistCount(data: ReportData) =
        data.checks.count { it in ReportSchema.allChecklistItems() }

    private fun update(value: ReportData) {
        report = value
        repository.save(value)
    }
}
