package br.com.dubrasil.rei.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CentralSyncClient(context: android.content.Context) {
    private val auth = AuthStore(context)

    fun send(report: ReportEntity): Result<Unit> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val payload = JSONObject()
            .put("reportId", report.reportId)
            .put("completedAt", report.completedAt ?: report.updatedAt)
            .put("report", JSONObject(report.payloadJson))
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
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Servidor respondeu HTTP $responseCode: $message")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchCompletedReports(limit: Int = 500): Result<List<ReportEntity>> = runCatching {
        val baseUrl = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(baseUrl.isNotBlank()) { "Servidor central nÃ£o configurado" }
        require(auth.token().isNotBlank()) { "UsuÃ¡rio nÃ£o autenticado" }

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
}
