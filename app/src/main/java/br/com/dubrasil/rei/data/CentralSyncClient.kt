package br.com.dubrasil.rei.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

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
                val message = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("Servidor respondeu HTTP $responseCode: $message")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun reportWithPrintableImages(report: JSONObject): JSONObject {
        val copy = JSONObject(report.toString())
        val fields = copy.optJSONObject("fields") ?: JSONObject().also { copy.put("fields", it) }
        listOf("assinaturaAnalistaImagem", "assinaturaClienteImagem").forEach { key ->
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

    fun fetchCompletedReports(limit: Int = 500): Result<List<ReportEntity>> = runCatching {
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
}
