package br.com.dubrasil.rei.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cliente do chat; a chave do provedor nunca é enviada pelo Android. */
class ChatClient(context: Context) {
    private val auth = AuthStore(context.applicationContext)

    fun createSession(reportId: String, skillCode: String): Result<ChatSessionRemote> = runCatching {
        val body = JSONObject().put("skill_code", skillCode).toString()
        val response = request("POST", "/api/levantamentos/${encode(reportId)}/chat/sessoes", body)
        val root = JSONObject(response.body)
        ChatSessionRemote(root.optString("id"), root.optString("reportId"), root.optString("skillCode"), root.optString("status"))
    }

    fun sendMessage(reportId: String, remoteSessionId: String, localIdempotencyKey: String, content: String): Result<ChatSendResult> = runCatching {
        val body = JSONObject()
            .put("sessionId", remoteSessionId)
            .put("localIdempotencyKey", localIdempotencyKey)
            .put("content", content)
            .toString()
        val response = request("POST", "/api/levantamentos/${encode(reportId)}/chat/mensagens", body)
        val root = JSONObject(response.body)
        ChatSendResult(root.optString("messageId"), root.optString("sessionId"), root.optString("status"), root.optJSONObject("response")?.toAssistant())
    }

    fun messages(reportId: String, remoteSessionId: String): Result<List<ChatRemoteMessage>> = runCatching {
        val query = "sessionId=${encode(remoteSessionId)}"
        val response = request("GET", "/api/levantamentos/${encode(reportId)}/chat/mensagens?$query", null)
        val array = JSONArray(response.body)
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val raw = item.opt("content")
            ChatRemoteMessage(
                id = item.optString("id"),
                localIdempotencyKey = item.optString("localIdempotencyKey"),
                role = item.optString("role"),
                content = if (raw is JSONObject) raw.toString() else raw?.toString().orEmpty(),
                status = item.optString("status"),
                createdAt = item.optString("createdAt"),
                errorMessage = item.optString("errorMessage")
            )
        }
    }

    private fun JSONObject.toAssistant() = ChatAssistantResponse(
        answer = optString("answer"),
        questions = optStringList("questions"), facts = optStringList("facts"),
        pendingItems = optStringList("pending_items"), risks = optStringList("risks"),
        suggestions = optStringList("suggestions"), evidenceIds = optStringList("evidence_ids"),
        requiresConfirmation = optBoolean("requires_confirmation"),
        confidence = optString("confidence", "medium"), skillCode = optString("skill_code")
    )

    private fun JSONObject.optStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.opt(index)
            when (item) { is JSONObject -> item.optString("label").ifBlank { item.optString("description") }.takeIf { it.isNotBlank() }; else -> item?.toString()?.takeIf { it.isNotBlank() } }
        }
    }

    private data class Response(val status: Int, val body: String)

    private fun request(method: String, path: String, body: String?): Response {
        val base = AuthStore.normalizeServerUrl(auth.serverUrl())
        require(base.isNotBlank()) { "Servidor central não configurado" }
        require(auth.token().isNotBlank()) { "Usuário não autenticado" }
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 6_000; readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer ${auth.token()}")
            setRequestProperty("Accept", "application/json")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val root = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                throw ChatApiException(status, root.optString("code"), root.optString("error").ifBlank { "Servidor respondeu HTTP $status" })
            }
            Response(status, text)
        } finally { connection.disconnect() }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
