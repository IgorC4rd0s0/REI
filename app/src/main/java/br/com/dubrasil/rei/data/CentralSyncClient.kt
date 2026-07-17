package br.com.dubrasil.rei.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import br.com.dubrasil.rei.BuildConfig
import br.com.dubrasil.rei.model.ReportSchema
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/** Erro de regra ou permissão devolvido pela API, que não deve ser tratado como falha de rede. */
class ServerReportException(
    val statusCode: Int,
    val code: String,
    val requirements: List<String>,
    message: String
) : IllegalStateException(message)

/**
 * Cliente HTTP usado pelo worker e pelo dashboard.
 *
 * O token nunca é incluído nos objetos persistidos do relatório. Imagens locais são convertidas
 * somente na cópia enviada ao servidor, preservando os URIs usados pelo aparelho.
 */
class CentralSyncClient(private val context: Context) {
    private val auth = AuthStore(context)

    fun send(report: ReportEntity): Result<Unit> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val payload = JSONObject()
            .put("reportId", report.reportId)
            .put("completedAt", report.completedAt ?: report.updatedAt)
            .put("report", reportWithPrintableImages(JSONObject(report.payloadJson)))
            .toString()

        val connection = (URL("$baseUrl/api/reports").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${auth.token()}")
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw serverException(responseCode, body)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun reportWithPrintableImages(report: JSONObject): JSONObject {
        val copy = JSONObject(report.toString())
        val fields = copy.optJSONObject("fields") ?: JSONObject().also { copy.put("fields", it) }
        listOf("assinaturaAnalistaImagem", "assinaturaClienteImagem").plus(photoFieldKeys()).forEach { key ->
            val value = fields.optString(key)
            if (value.isNotBlank()) {
                fields.put(key, printableImageDataUrl(value, "image/png") ?: value)
            }
        }

        val attachments = copy.optJSONArray("attachments") ?: return copy
        for (index in 0 until attachments.length()) {
            val item = attachments.optJSONObject(index) ?: continue
            val uri = item.optString("uri")
            val mimeType = item.optString("mimeType").ifBlank {
                runCatching { context.contentResolver.getType(Uri.parse(uri)).orEmpty() }.getOrDefault("")
            }
            if (mimeType.startsWith("image/")) {
                printableImageDataUrl(uri, mimeType)?.let { dataUrl ->
                    item.put("uri", dataUrl)
                    item.put("mimeType", if (dataUrl.startsWith("data:image/jpeg")) "image/jpeg" else mimeType)
                }
            }
        }
        return copy
    }

    private fun photoFieldKeys(): List<String> = buildList {
        addAll(ReportSchema.surveySections.flatMap { section ->
            section.fields.filter { it.type.equals("photo", ignoreCase = true) }.map { it.key }
        })
        listOf("tecnico", "estoque", "financeiro", "fiscal", "supervisao").forEach { scope ->
            addAll(ReportSchema.dynamicFields(scope).flatMap { group ->
                group.fields.filter { it.type.equals("photo", ignoreCase = true) }.map { it.key }
            })
        }
    }.distinct()

    private fun printableImageDataUrl(value: String, fallbackMimeType: String): String? {
        if (value.startsWith("data:image")) return value
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        return runCatching {
            val mimeType = runCatching { context.contentResolver.getType(uri).orEmpty() }
                .getOrDefault("")
                .ifBlank { fallbackMimeType }
            if (mimeType.equals("image/png", ignoreCase = true) && value.contains("signatures", ignoreCase = true)) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    "data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}"
                }
            } else {
                val bitmap = decodeScaledBitmap(uri) ?: return@runCatching null
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
                bitmap.recycle()
                "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(output.toByteArray())}"
            }
        }.getOrNull()
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    fun fetchCompletedReports(limit: Int = 1000): Result<List<ReportEntity>> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }

        val connection = (URL("$baseUrl/api/reports?full=1&limit=$limit").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${auth.token()}")
        }
        try {
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Servidor respondeu HTTP $responseCode" })
            }

            val now = System.currentTimeMillis()
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val report = item.optJSONObject("report") ?: return@mapNotNull null
                ReportEntity(
                    dbId = "${ReportEntity.STATUS_COMPLETED}:$id",
                    reportId = id,
                    status = ReportEntity.STATUS_COMPLETED,
                    client = item.optString("client"),
                    consultant = item.optString("consultant"),
                    deliveryStatus = item.optString("delivery_status"),
                    checkedItems = item.optInt("checked_items"),
                    completedAt = item.optLong("completed_at").takeIf { it > 0L },
                    updatedAt = now,
                    payloadJson = report.toString(),
                    syncStatus = ReportEntity.SYNC_SYNCED,
                    lastSyncAttempt = now,
                    syncError = null
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchSchemaOverrides(): Result<Unit> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }

        val connection = (URL("$baseUrl/api/schema-overrides").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${auth.token()}")
        }
        try {
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Servidor respondeu HTTP $responseCode" })
            }
            SchemaStore(context).save(body)
        } finally {
            connection.disconnect()
        }
    }

    fun sendHeartbeat(pendingCount: Int, lastError: String?): Result<Unit> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        val user = auth.currentUser() ?: error("Usuário não autenticado")
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val body = JSONObject()
            .put("username", user.username)
            .put("deviceId", auth.deviceId())
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("lastSeen", System.currentTimeMillis())
            .put("pendingCount", pendingCount.coerceAtLeast(0))
            .put("lastError", lastError.orEmpty().take(500))
            .toString()
        authenticatedJsonConnection("$baseUrl/api/device-heartbeats", "POST", body).also { response ->
            if (response.first !in 200..299) throw serverException(response.first, response.second)
        }
    }

    fun fetchDeviceStatuses(): Result<List<DeviceSyncStatus>> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val (status, body) = authenticatedJsonConnection("$baseUrl/api/device-heartbeats", "GET")
        if (status !in 200..299) throw serverException(status, body)
        val array = JSONArray(body)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            DeviceSyncStatus(
                username = item.optString("username"),
                deviceId = item.optString("deviceId"),
                appVersion = item.optString("appVersion"),
                lastSeen = item.optString("lastSeen"),
                pendingCount = item.optInt("pendingCount"),
                lastError = item.optString("lastError").takeIf { it.isNotBlank() }
            )
        }
    }

    fun fetchSupervisorDashboard(filters: SupervisorDashboardFilters): Result<SupervisorDashboard> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val values = linkedMapOf(
            "period" to filters.period,
            "staleDays" to filters.staleDays,
            "implantador" to filters.implantador,
            "stage" to filters.stage,
            "overdue" to if (filters.overdue) "1" else "0",
            "blockers" to if (filters.blockers) "1" else "0"
        )
        val query = values.entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8") }=${URLEncoder.encode(value, "UTF-8") }"
        }
        val (status, body) = authenticatedJsonConnection("$baseUrl/api/dashboard/supervisor?$query", "GET")
        if (status !in 200..299) throw serverException(status, body)
        parseSupervisorDashboard(JSONObject(body))
    }

    private fun parseSupervisorDashboard(root: JSONObject): SupervisorDashboard {
        val indicators = root.optJSONObject("indicators") ?: JSONObject()
        val lists = root.optJSONObject("lists") ?: JSONObject()
        val filterOptions = root.optJSONObject("filterOptions") ?: JSONObject()
        fun records(name: String): List<DashboardRecordSummary> {
            val array = lists.optJSONArray(name) ?: JSONArray()
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DashboardRecordSummary(
                    id = item.optString("id"), client = item.optString("client"),
                    stageLabel = item.optString("stageLabel"), assignedName = item.optString("assignedName"),
                    deadline = item.optString("deadline").takeIf { it.isNotBlank() },
                    daysStale = item.optInt("daysStale"), blocker = item.optString("blocker").takeIf { it.isNotBlank() }
                )
            }
        }
        fun options(name: String, valueKey: String, labelKey: String): List<DashboardFilterOption> {
            val array = filterOptions.optJSONArray(name) ?: JSONArray()
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                DashboardFilterOption(item.optString(valueKey), item.optString(labelKey))
            }
        }
        val workloadArray = root.optJSONArray("workload") ?: JSONArray()
        val syncArray = lists.optJSONArray("syncErrors") ?: JSONArray()
        val stagesArray = root.optJSONArray("byStage") ?: JSONArray()
        return SupervisorDashboard(
            generatedAt = root.optString("generatedAt"),
            indicators = DashboardIndicators(
                total = indicators.optInt("total"), overdue = indicators.optInt("overdue"),
                stale = indicators.optInt("stale"), pendingEvaluations = indicators.optInt("pendingEvaluations"),
                blockers = indicators.optInt("blockers"), concludedMonth = indicators.optInt("concludedMonth"),
                averageDurationDays = indicators.optDouble("averageDurationDays").takeUnless { indicators.isNull("averageDurationDays") },
                averageScore = indicators.optDouble("averageScore").takeUnless { indicators.isNull("averageScore") },
                syncErrors = indicators.optInt("syncErrors")
            ),
            byStage = (0 until stagesArray.length()).map { index ->
                stagesArray.getJSONObject(index).let { it.optString("label") to it.optInt("count") }
            },
            workload = (0 until workloadArray.length()).map { index ->
                val item = workloadArray.getJSONObject(index)
                DashboardWorkload(
                    username = item.optString("username"), fullName = item.optString("fullName"),
                    active = item.optInt("active"), overdue = item.optInt("overdue"), stale = item.optInt("stale"),
                    blockers = item.optInt("blockers"), pendingEvaluations = item.optInt("pendingEvaluations"),
                    concludedMonth = item.optInt("concludedMonth"), lastSync = item.optString("lastSync").takeIf { it.isNotBlank() },
                    pendingSync = item.optInt("pendingSync"), syncErrors = item.optInt("syncErrors")
                )
            },
            overdue = records("overdue"), stale = records("stale"),
            pendingEvaluations = records("pendingEvaluations"), blockers = records("blockers"),
            syncErrors = (0 until syncArray.length()).map { index ->
                val item = syncArray.getJSONObject(index)
                DashboardSyncError(
                    fullName = item.optString("fullName"), appVersion = item.optString("appVersion"),
                    lastSeen = item.optString("lastSeen"), pendingCount = item.optInt("pendingCount"),
                    error = item.optString("error")
                )
            },
            implantadores = options("implantadores", "username", "full_name"),
            stages = options("stages", "value", "label")
        )
    }

    private fun authenticatedJsonConnection(url: String, method: String, body: String? = null): Pair<Int, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${auth.token()}")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            status to response
        } finally {
            connection.disconnect()
        }
    }

    private fun serverException(status: Int, body: String): ServerReportException {
        val root = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
        val code = root.optString("code")
        val requirementsArray = root.optJSONArray("requirements") ?: JSONArray()
        val requirements = (0 until requirementsArray.length()).mapNotNull { index ->
            requirementsArray.optJSONObject(index)?.let { item ->
                val label = item.optString("label").trim()
                val section = item.optString("section").trim()
                if (label.isBlank()) null else if (section.isBlank()) label else "$section: $label"
            }
        }
        val baseMessage = root.optString("error").ifBlank { "Servidor respondeu HTTP $status" }
        val message = if (code == "required_items_missing" && requirements.isNotEmpty()) {
            "$baseMessage — ${requirements.joinToString("; ")}"
        } else baseMessage
        return ServerReportException(status, code, requirements, message)
    }
}
