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
            if (ownerUsername.isNotBlank()) put("_ownerUsername", ownerUsername)
        })
        repository.clear()
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
