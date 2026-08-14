package br.com.dubrasil.rei.data

import android.content.Context
import br.com.dubrasil.rei.model.ImplementationSummary
import br.com.dubrasil.rei.model.ReportAttachment
import br.com.dubrasil.rei.model.ReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Camada de persistência do aplicativo.
 *
 * As gravações são serializadas em uma única fila para preservar a ordem das alterações locais.
 * Cada relatório é salvo individualmente, evitando recolocar todo o histórico na fila de sync.
 */
class ReportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = ReiDatabase.getInstance(appContext).reportDao()
    private val chatDao = ReiDatabase.getInstance(appContext).chatDao()
    private val writes = Executors.newSingleThreadExecutor()
    private val legacyPrefs = appContext.getSharedPreferences("rei_report", Context.MODE_PRIVATE)

    init {
        SchemaStore(appContext).applyCached()
        // A migração é idempotente: instalações antigas podem abrir sem perder rascunhos.
        migrateLegacyStorage()
        SyncScheduler.enqueue(appContext)
    }

    fun load(): ReportData = runBlocking(Dispatchers.IO) {
        dao.getDraft()?.let { decodeReport(JSONObject(it.payloadJson)) } ?: ReportData()
    }

    fun save(data: ReportData) {
        writes.execute { dao.upsert(toDraftEntity(data)) }
    }

    fun clear() {
        writes.execute { dao.deleteDraft() }
    }

    fun loadHistory(): List<ImplementationSummary> = runBlocking(Dispatchers.IO) {
        dao.getCompleted().map(::toSummary)
    }

    fun upsertHistoryItem(item: ImplementationSummary) {
        writes.execute {
            dao.upsert(toHistoryEntity(item))
            SyncScheduler.enqueue(appContext)
        }
    }

    /** Confirma o rascunho no Room antes de a interface sair da tela de levantamento. */
    fun persistSurveyDraft(item: ImplementationSummary): Result<Unit> = runWriteAndWait {
        val history = toHistoryEntity(item)
        val draft = toDraftEntity(item.report)
        dao.upsertHistoryAndDraft(history, draft)
        check(dao.getCompletedByReportId(item.id)?.payloadJson == history.payloadJson) {
            "O levantamento não foi confirmado no histórico local."
        }
        check(dao.getDraft()?.payloadJson == draft.payloadJson) {
            "O rascunho do levantamento não foi confirmado no armazenamento local."
        }
    }.onSuccess {
        SyncScheduler.enqueue(appContext)
    }

    /** Conclui o levantamento e remove o rascunho na mesma transação local. */
    fun persistCompletedSurvey(item: ImplementationSummary): Result<Unit> = runWriteAndWait {
        val history = toHistoryEntity(item)
        dao.upsertHistoryAndDeleteDraft(history)
        check(dao.getCompletedByReportId(item.id)?.payloadJson == history.payloadJson) {
            "O levantamento concluído não foi confirmado no armazenamento local."
        }
        check(dao.getDraft() == null) {
            "O rascunho permaneceu aberto após a conclusão do levantamento."
        }
    }.onSuccess {
        SyncScheduler.enqueue(appContext)
    }

    fun loadSyncDiagnostic(): SyncDiagnostic = runBlocking(Dispatchers.IO) {
        val auth = AuthStore(appContext)
        val roomAttempt = dao.latestSyncAttempt()
        val storedAttempt = auth.lastSyncAttempt()
        SyncDiagnostic(
            serverUrl = AuthStore.normalizeServerUrl(auth.serverUrl()),
            serverConfigured = AuthStore.normalizeServerUrl(auth.serverUrl()).isNotBlank(),
            username = auth.currentUser()?.username,
            userAuthenticated = auth.currentUser() != null && auth.token().isNotBlank(),
            lastAttempt = listOfNotNull(roomAttempt, storedAttempt).maxOrNull(),
            pendingCount = dao.countPendingSync(),
            lastError = auth.lastSyncError() ?: dao.latestSyncError()
        )
    }

    fun loadDeviceStatuses(): List<DeviceSyncStatus> =
        CentralSyncClient(appContext).fetchDeviceStatuses().getOrDefault(emptyList())

    fun latestChatSession(reportId: String): ChatSessionEntity? = runBlocking(Dispatchers.IO) {
        chatDao.latestSession(reportId)
    }

    fun chatSession(sessionId: String): ChatSessionEntity? = runBlocking(Dispatchers.IO) {
        chatDao.session(sessionId)
    }

    fun createLocalChatSession(reportId: String, skillCode: String): ChatSessionEntity = runBlocking(Dispatchers.IO) {
        chatDao.latestSession(reportId)?.takeIf { it.skillCode == skillCode } ?: ChatSessionEntity(
            id = UUID.randomUUID().toString(), reportId = reportId, skillCode = skillCode,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
            syncStatus = ChatSessionEntity.PENDING
        ).also(chatDao::upsertSession)
    }

    fun markRemoteChatSession(sessionId: String, serverId: String) = writes.execute {
        chatDao.markRemoteSession(sessionId, serverId, System.currentTimeMillis())
    }

    fun loadChatMessages(sessionId: String): List<ChatMessageEntity> = runBlocking(Dispatchers.IO) {
        chatDao.messages(sessionId)
    }

    fun saveChatMessage(message: ChatMessageEntity) = writes.execute { chatDao.upsertMessage(message) }

    fun updateChatMessage(
        id: String, status: String, sentAt: Long? = null, receivedAt: Long? = null,
        serverResponseId: String = "", errorMessage: String? = null
    ) = writes.execute { chatDao.updateStatus(id, status, sentAt, receivedAt, serverResponseId, errorMessage) }

    fun retryChatMessage(id: String) = writes.execute { chatDao.retry(id) }

    fun pendingChatMessages(limit: Int = 20): List<ChatMessageEntity> = runBlocking(Dispatchers.IO) {
        chatDao.pendingMessages(limit)
    }

    fun pendingChatCount(): Int = runBlocking(Dispatchers.IO) { chatDao.countPending() }

    fun loadSupervisorDashboard(filters: SupervisorDashboardFilters): SupervisorDashboard? =
        CentralSyncClient(appContext).fetchSupervisorDashboard(filters).getOrNull()

    fun syncNow(): SyncRunResult {
        val client = CentralSyncClient(appContext)
        val auth = AuthStore(appContext)
        val attempt = System.currentTimeMillis()
        auth.beginSyncAttempt(attempt)
        var failed = false
        var attemptError: String? = null
        client.fetchSchemaOverrides().onFailure { error ->
            attemptError = error.message ?: "Não foi possível atualizar os itens dos relatórios."
        }
        dao.getPendingSync().forEach { entity ->
            client.send(entity)
                .onSuccess { dao.updateSyncStatus(entity.dbId, ReportEntity.SYNC_SYNCED, attempt, null) }
                .onFailure { error ->
                    failed = true
                    attemptError = error.message ?: "Falha ao sincronizar ${entity.client}."
                    dao.updateSyncStatus(entity.dbId, ReportEntity.SYNC_ERROR, attempt, error.message?.take(500))
                }
        }
        if (!failed) {
            client.fetchCompletedReports()
                .onSuccess { remoteReports ->
                    if (remoteReports.isNotEmpty()) dao.upsertAll(remoteReports)
                }
                .onFailure { error ->
                    attemptError = error.message ?: "Não foi possível consultar o servidor."
                }
        }
        val pendingCount = dao.countPendingSync()
        client.sendHeartbeat(pendingCount, attemptError).onFailure { error ->
            if (attemptError == null) attemptError = error.message ?: "Falha ao enviar diagnóstico ao servidor."
        }
        auth.finishSyncAttempt(attemptError)
        val diagnostic = loadSyncDiagnostic()
        return SyncRunResult(diagnostic, attemptError)
    }

    private fun toSummary(entity: ReportEntity): ImplementationSummary = ImplementationSummary(
        id = entity.reportId,
        client = entity.client,
        consultant = entity.consultant,
        completedAt = entity.completedAt ?: entity.updatedAt,
        deliveryStatus = entity.deliveryStatus,
        checkedItems = entity.checkedItems,
        report = runCatching { decodeReport(JSONObject(entity.payloadJson)) }.getOrDefault(ReportData()),
        syncStatus = entity.syncStatus,
        lastSyncAttempt = entity.lastSyncAttempt,
        syncError = entity.syncError
    )

    private fun toDraftEntity(data: ReportData): ReportEntity {
        val reportId = data.field("_id").ifBlank { "active_draft" }
        return ReportEntity(
            dbId = "${ReportEntity.STATUS_DRAFT}:$reportId",
            reportId = reportId,
            status = ReportEntity.STATUS_DRAFT,
            client = data.field("cliente"),
            consultant = data.field("consultor"),
            deliveryStatus = data.deliveryStatus,
            checkedItems = data.checks.size,
            completedAt = null,
            updatedAt = System.currentTimeMillis(),
            payloadJson = encodeReport(data).toString()
        )
    }

    private fun toHistoryEntity(item: ImplementationSummary) = ReportEntity(
        dbId = "${ReportEntity.STATUS_COMPLETED}:${item.id}",
        reportId = item.id,
        status = ReportEntity.STATUS_COMPLETED,
        client = item.client,
        consultant = item.consultant,
        deliveryStatus = item.deliveryStatus,
        checkedItems = item.checkedItems,
        completedAt = item.completedAt,
        updatedAt = System.currentTimeMillis(),
        payloadJson = encodeReport(item.report).toString()
    )

    private fun runWriteAndWait(block: () -> Unit): Result<Unit> = runCatching {
        writes.submit(block).get()
    }

    private fun migrateLegacyStorage() {
        if (legacyPrefs.getBoolean("room_migration_done", false)) return
        runBlocking(Dispatchers.IO) {
            if (dao.count() == 0) {
                legacyPrefs.getString("draft", null)?.takeIf { it.isNotBlank() && it != "{}" }?.let { json ->
                    runCatching {
                        val report = decodeReport(JSONObject(json))
                        val reportId = report.field("_id").ifBlank { UUID.randomUUID().toString() }
                        dao.upsert(ReportEntity(
                            dbId = "${ReportEntity.STATUS_DRAFT}:$reportId",
                            reportId = reportId,
                            status = ReportEntity.STATUS_DRAFT,
                            client = report.field("cliente"),
                            consultant = report.field("consultor"),
                            deliveryStatus = report.deliveryStatus,
                            checkedItems = report.checks.size,
                            completedAt = null,
                            updatedAt = System.currentTimeMillis(),
                            payloadJson = encodeReport(report).toString()
                        ))
                    }
                }

                val history = runCatching {
                    val array = JSONArray(legacyPrefs.getString("history", "[]") ?: "[]")
                    (0 until array.length()).map { index ->
                        val item = array.getJSONObject(index)
                        val id = item.optString("id").ifBlank { UUID.randomUUID().toString() }
                        val report = item.optJSONObject("report")?.let(::decodeReport) ?: ReportData(
                            fields = mapOf(
                                "_id" to id,
                                "cliente" to item.optString("client"),
                                "consultor" to item.optString("consultant")
                            ),
                            deliveryStatus = item.optString("deliveryStatus")
                        )
                        ReportEntity(
                            dbId = "${ReportEntity.STATUS_COMPLETED}:$id",
                            reportId = id,
                            status = ReportEntity.STATUS_COMPLETED,
                            client = item.optString("client"),
                            consultant = item.optString("consultant"),
                            deliveryStatus = item.optString("deliveryStatus"),
                            checkedItems = item.optInt("checkedItems"),
                            completedAt = item.optLong("completedAt"),
                            updatedAt = System.currentTimeMillis(),
                            payloadJson = encodeReport(report).toString()
                        )
                    }
                }.getOrDefault(emptyList())
                if (history.isNotEmpty()) dao.upsertAll(history)
            }
        }
        legacyPrefs.edit().putBoolean("room_migration_done", true).apply()
    }

    private fun decodeReport(root: JSONObject): ReportData {
        val fieldsJson = root.optJSONObject("fields") ?: JSONObject()
        val fields = fieldsJson.keys().asSequence().associateWith { fieldsJson.optString(it) }
        val checksJson = root.optJSONArray("checks") ?: JSONArray()
        val checks = (0 until checksJson.length()).map { checksJson.getString(it) }.toSet()
        val attachmentsJson = root.optJSONArray("attachments") ?: JSONArray()
        val attachments = (0 until attachmentsJson.length()).map { index ->
            val item = attachmentsJson.getJSONObject(index)
            ReportAttachment(item.getString("uri"), item.optString("name", "Arquivo"), item.optString("mimeType"))
        }
        return ReportData(fields, checks, root.optString("deliveryStatus"), root.optString("rating"), attachments)
    }

    private fun encodeReport(data: ReportData): JSONObject {
        val fields = JSONObject().apply { data.fields.forEach { (key, value) -> put(key, value) } }
        return JSONObject()
            .put("fields", fields)
            .put("checks", JSONArray(data.checks.toList()))
            .put("deliveryStatus", data.deliveryStatus)
            .put("rating", data.rating)
            .put("attachments", JSONArray().apply {
                data.attachments.forEach { item ->
                    put(JSONObject().put("uri", item.uri).put("name", item.name).put("mimeType", item.mimeType))
                }
            })
    }
}
