package br.com.dubrasil.rei.data

import br.com.dubrasil.rei.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CentralSyncClient(context: android.content.Context) {
    private val auth = AuthStore(context)

    fun send(report: ReportEntity): Result<Unit> = runCatching {
        require(BuildConfig.CENTRAL_API_URL.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val payload = JSONObject()
            .put("reportId", report.reportId)
            .put("completedAt", report.completedAt ?: report.updatedAt)
            .put("report", JSONObject(report.payloadJson))
            .toString()

        val connection = (URL("${BuildConfig.CENTRAL_API_URL.trimEnd('/')}/api/reports").openConnection() as HttpURLConnection).apply {
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
}
