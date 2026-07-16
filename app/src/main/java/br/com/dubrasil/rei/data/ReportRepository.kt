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

class ReportRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = ReiDatabase.getInstance(appContext).reportDao()
    private val writes = Executors.newSingleThreadExecutor()
    private val legacyPrefs = appContext.getSharedPreferences("rei_report", Context.MODE_PRIVATE)

    init {
        SchemaStore(appContext).applyCached()
        migrateLegacyStorage()
        SyncScheduler.enqueue(appContext)
    }

    fun load(): ReportData = runBlocking(Dispatchers.IO) {
        dao.getDraft()?.let { decodeReport(JSONObject(it.payloadJson)) } ?: ReportData()
    }

    fun save(data: ReportData) {
        val reportId = data.field("_id").ifBlank { "active_draft" }
        val entity = ReportEntity(
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
        writes.execute { dao.upsert(entity) }
    }

    fun clear() {
        writes.execute { dao.deleteDraft() }
    }

    fun loadHistory(): List<ImplementationSummary> = runBlocking(Dispatchers.IO) {
        dao.getCompleted().map(::toSummary)
    }

    fun upsertHistoryItem(item: ImplementationSummary) {
        val entity = ReportEntity(
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
        writes.execute {
            dao.upsert(entity)
            SyncScheduler.enqueue(appContext)
        }
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
