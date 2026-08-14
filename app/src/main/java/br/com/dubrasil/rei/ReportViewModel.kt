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
import br.com.dubrasil.rei.data.SupervisorDashboard
import br.com.dubrasil.rei.data.SupervisorDashboardFilters
import br.com.dubrasil.rei.data.AuthStore
import br.com.dubrasil.rei.data.ChatClient
import br.com.dubrasil.rei.data.ChatMessageEntity
import br.com.dubrasil.rei.data.ChatSessionEntity
import br.com.dubrasil.rei.data.ChatAssistantResponse
import br.com.dubrasil.rei.data.ChatSendResult
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportAttachment
import br.com.dubrasil.rei.model.ImplementationSummary
import br.com.dubrasil.rei.model.ReportSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import org.json.JSONObject

/**
 * Estado central das telas Android.
 *
 * Mantém o formulário atual, o histórico local e os diagnósticos de sincronização. As operações
 * de persistência ficam no [ReportRepository] para que as telas Compose não acessem o Room ou a
 * rede diretamente.
 */
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
    var supervisorDashboard by mutableStateOf<SupervisorDashboard?>(null)
        private set
    var supervisorFilters by mutableStateOf(SupervisorDashboardFilters())
        private set
    var isDashboardLoading by mutableStateOf(false)
        private set
    var chatSession by mutableStateOf<ChatSessionEntity?>(null)
        private set
    var chatMessages by mutableStateOf<List<ChatMessageEntity>>(emptyList())
        private set
    var chatLoading by mutableStateOf(false)
        private set
    var chatError by mutableStateOf<String?>(null)
        private set

    init {
        SchemaStore(application).applyCached()
    }

    fun setField(key: String, value: String) = update(report.copy(fields = report.fields + (key to value)))

    fun toggle(item: String, aliases: Collection<String> = listOf(item)) {
        val checked = aliases.any { it in report.checks }
        val updated = if (checked) report.checks - aliases.toSet() else report.checks + item
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

    fun saveSurveyDraft(): Result<ImplementationSummary> = persistSurvey("levantamento_pendente")

    fun completeSurvey(): Result<ImplementationSummary> {
        val missing = ReportSchema.validateRequiredRequirements(report, ReportSchema.PHASE_SURVEY)
        if (missing.isNotEmpty()) {
            return Result.failure(IllegalStateException("Existem itens obrigatórios pendentes."))
        }
        val snapshot = ReportSchema.validationSnapshot(report, ReportSchema.PHASE_SURVEY)
        return persistSurvey(
            "rei_pendente",
            report.copy(fields = report.fields + ("_requiredValidationSnapshot" to snapshot))
        )
    }

    private fun persistSurvey(stage: String, source: ReportData = report): Result<ImplementationSummary> {
        val id = source.field("_id").ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val data = source.copy(fields = source.fields + buildMap {
            put("_id", id)
            put("_stage", stage)
            put("cliente", source.field("cliente").ifBlank { source.field("empresa") })
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
        val persisted = if (stage == "rei_pendente") {
            repository.persistCompletedSurvey(summary)
        } else {
            repository.persistSurveyDraft(summary)
        }
        return persisted.map {
            history = (history.filterNot { item -> item.id == id } + summary)
                .sortedByDescending { item -> item.completedAt }
            report = if (stage == "rei_pendente") ReportData() else data
            if (stage == "levantamento_pendente") {
                ReiReminderScheduler.scheduleSurveyReminder(
                    getApplication(),
                    id,
                    data.field("cliente").ifBlank { data.field("empresa") },
                    data.field("_surveyScheduledAt")
                )
            }
            summary
        }
    }

    fun saveSupervisorEvaluation(id: String, supervisorName: String, score: String, rating: String, supervisionChecks: Set<String>) {
        val item = history.firstOrNull { it.id == id } ?: return
        val supervisionKeys = ReportSchema.supervision.flatMap { group ->
            group.items.flatMap { ReportSchema.itemKeys("supervisao", group.title, it) }
        }.toSet()
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

    fun updateSupervisorFilters(filters: SupervisorDashboardFilters) {
        supervisorFilters = filters
        isDashboardLoading = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                repository.loadSupervisorDashboard(filters)
            }
            if (supervisorFilters == filters) {
                supervisorDashboard = loaded ?: supervisorDashboard
                isDashboardLoading = false
            }
        }
    }

    fun consumeServerMessage() {
        serverMessage = null
    }

    fun openChat(reportId: String, skillCode: String = "erp-levantamento-diagnostico") {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.latestChatSession(reportId)
                ?.takeIf { it.skillCode == skillCode }
                ?: repository.createLocalChatSession(reportId, skillCode)
            val messages = repository.loadChatMessages(session.id)
            withContext(Dispatchers.Main) {
                chatSession = session
                chatMessages = messages
                chatError = null
            }
        }
    }

    fun changeChatSkill(reportId: String, skillCode: String) {
        openChat(reportId, skillCode)
    }

    fun sendChatMessage(reportId: String, content: String) {
        val text = content.trim()
        if (text.isBlank() || chatLoading) return
        val session = chatSession ?: repository.createLocalChatSession(reportId, "erp-levantamento-diagnostico").also { chatSession = it }
        val now = System.currentTimeMillis()
        val message = ChatMessageEntity(
            id = UUID.randomUUID().toString(), localIdempotencyKey = UUID.randomUUID().toString(),
            sessionId = session.id, reportId = reportId, role = ChatMessageEntity.ROLE_USER,
            content = text, status = ChatMessageEntity.STATUS_PENDING, createdAt = now
        )
        repository.saveChatMessage(message)
        chatMessages = chatMessages + message
        chatError = null
        if (!hasInternetNetwork()) {
            chatError = "Sem conexão: mensagem salva no dispositivo e será enviada quando a rede voltar."
            return
        }
        dispatchChatMessage(reportId, session, message)
    }

    fun retryChatMessage(reportId: String, messageId: String) {
        repository.retryChatMessage(messageId)
        chatMessages = chatMessages.map { if (it.id == messageId) it.copy(status = ChatMessageEntity.STATUS_PENDING, errorMessage = null) else it }
        val message = chatMessages.firstOrNull { it.id == messageId }
        val session = message?.let { repository.chatSession(it.sessionId) }
        if (message != null && session != null && hasInternetNetwork()) dispatchChatMessage(reportId, session, message)
    }

    private fun dispatchChatMessage(reportId: String, session: ChatSessionEntity, message: ChatMessageEntity) {
        chatLoading = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val client = ChatClient(getApplication())
                var remoteId = session.serverConversationId
                if (remoteId.isBlank()) {
                    val created = client.createSession(reportId, session.skillCode).getOrElse { error ->
                        repository.updateChatMessage(message.id, ChatMessageEntity.STATUS_FAILED, errorMessage = error.message)
                        return@withContext Result.failure<ChatSendResult>(error)
                    }
                    remoteId = created.id
                    repository.markRemoteChatSession(session.id, remoteId)
                }
                client.sendMessage(reportId, remoteId, message.localIdempotencyKey, message.content)
                    .onSuccess { response ->
                        repository.updateChatMessage(message.id, ChatMessageEntity.STATUS_SENT, sentAt = System.currentTimeMillis(), serverResponseId = response.messageId)
                        response.response?.let { assistant ->
                            repository.saveChatMessage(ChatMessageEntity(
                                id = UUID.randomUUID().toString(), localIdempotencyKey = "${message.localIdempotencyKey}:assistant",
                                sessionId = session.id, reportId = reportId, role = ChatMessageEntity.ROLE_ASSISTANT,
                                content = assistant.toJson().toString(), status = ChatMessageEntity.STATUS_RECEIVED,
                                createdAt = System.currentTimeMillis(), receivedAt = System.currentTimeMillis(), serverResponseId = response.messageId
                            ))
                        }
                    }
                    .map { it }
            }
            chatMessages = withContext(Dispatchers.IO) { repository.loadChatMessages(session.id) }
            chatError = result.exceptionOrNull()?.message
            chatLoading = false
        }
    }

    fun clearChatError() { chatError = null }

    private fun ChatAssistantResponse.toJson() = JSONObject().apply {
        put("answer", answer); put("questions", questions); put("facts", facts); put("pending_items", pendingItems)
        put("risks", risks); put("suggestions", suggestions); put("evidence_ids", evidenceIds)
        put("requires_confirmation", requiresConfirmation); put("confidence", confidence); put("skill_code", skillCode)
    }

    private fun deliveryChecklistCount(data: ReportData) = ReportSchema.deliveryChecklistCount(data)

    private fun hasInternetNetwork(): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun update(value: ReportData) {
        report = value
        repository.save(value)
    }
}
