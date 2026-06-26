package br.com.dubrasil.rei.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthClient(private val context: Context) {
    fun login(
        username: String,
        password: String,
        serverUrl: String = AuthStore(context).serverUrl()
    ): Result<AuthSession> = runCatching {
        val localAuth = LocalAuthRepository(context)
        val baseUrl = AuthStore.normalizeServerUrl(serverUrl)

        if (baseUrl.isBlank()) {
            val offline = localAuth.login(username, password)
                ?: error("Usuário não encontrado no login offline. Conecte uma vez na rede do escritório para liberar este acesso.")
            AuthStore(context).save(offline, localAuth.serverUrl(username))
            return@runCatching offline
        }

        tryOnlineLogin(username, password, baseUrl)
            .onSuccess { session ->
                AuthStore(context).save(session, baseUrl)
                localAuth.cacheSession(session, password, baseUrl)
                SyncScheduler.enqueue(context)
            }
            .getOrElse { onlineError ->
                val offline = localAuth.login(username, password)
                    ?: throw onlineError
                AuthStore(context).save(offline, localAuth.serverUrl(username).ifBlank { baseUrl })
                offline
            }
    }

    private fun tryOnlineLogin(username: String, password: String, baseUrl: String): Result<AuthSession> = runCatching {
        val connection = (URL("$baseUrl/api/auth/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val request = JSONObject()
                .put("username", username.trim())
                .put("password", password)
                .toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Não foi possível entrar (HTTP $code)" })
            }
            val json = JSONObject(body)
            val userJson = json.getJSONObject("user")
            AuthSession(
                token = json.getString("token"),
                user = AuthUser(
                    id = userJson.getInt("id"),
                    username = userJson.getString("username"),
                    fullName = userJson.getString("fullName"),
                    role = userJson.getString("role")
                )
            )
        } finally {
            connection.disconnect()
        }
    }
}
