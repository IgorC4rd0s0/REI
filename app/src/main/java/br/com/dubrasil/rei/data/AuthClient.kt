package br.com.dubrasil.rei.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Executa autenticação e troca de senha contra o servidor configurado no aparelho. */
class AuthClient(private val context: Context) {
    fun login(
        username: String,
        password: String,
        serverUrl: String = AuthStore(context).serverUrl()
    ): Result<AuthSession> = runCatching {
        val localAuth = LocalAuthRepository(context)
        val baseUrl = AuthStore.normalizeServerUrl(serverUrl)

        if (baseUrl.isBlank()) {
            val offline = localAuth.login(username, password)?.withStoredPhoto()
                ?: error("Usuário não encontrado no login offline. Conecte uma vez na rede do escritório para liberar este acesso.")
            AuthStore(context).save(offline, localAuth.serverUrl(username))
            return@runCatching offline
        }

        tryOnlineLogin(username, password, baseUrl)
            .onSuccess { session ->
                AuthStore(context).save(session, baseUrl)
                localAuth.cacheSession(session, password, baseUrl)
                CentralSyncClient(context).fetchSchemaOverrides()
                SyncScheduler.enqueue(context)
            }
            .getOrElse { onlineError ->
                val offline = localAuth.login(username, password)?.withStoredPhoto()
                    ?: throw onlineError
                AuthStore(context).save(offline, localAuth.serverUrl(username).ifBlank { baseUrl })
                offline
            }
    }

    private fun AuthSession.withStoredPhoto(): AuthSession {
        val stored = AuthStore(context).currentUser()
        if (stored?.username.equals(user.username, ignoreCase = true) && stored?.photoData?.isNotBlank() == true) {
            return copy(user = user.copy(photoData = stored.photoData))
        }
        return this
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
                    role = userJson.getString("role"),
                    photoData = userJson.optString("photoData")
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val store = AuthStore(context)
        val baseUrl = AuthStore.normalizeServerUrl(store.serverUrl())
        val token = store.token()
        val user = store.currentUser() ?: error("Sessão de usuário não encontrada.")
        if (baseUrl.isBlank()) error("Conecte-se à rede do escritório para alterar a senha.")
        if (token.isBlank() || token.startsWith("offline:")) {
            error("Entre novamente conectado à rede do escritório antes de alterar a senha.")
        }

        val connection = (URL("$baseUrl/api/auth/change-password").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val request = JSONObject()
                .put("currentPassword", currentPassword)
                .put("newPassword", newPassword)
                .put("confirmation", newPassword)
                .toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Não foi possível alterar a senha (HTTP $code)" })
            }
            LocalAuthRepository(context).cacheSession(AuthSession(token, user), newPassword, baseUrl)
        } finally {
            connection.disconnect()
        }
    }

    fun updateProfilePhoto(photoData: String): Result<AuthUser> = runCatching {
        val store = AuthStore(context)
        val baseUrl = AuthStore.normalizeServerUrl(store.serverUrl())
        val token = store.token()
        if (baseUrl.isBlank() || token.isBlank() || token.startsWith("offline:")) {
            error("Conecte-se à rede do escritório para atualizar a foto.")
        }
        val connection = (URL("$baseUrl/api/auth/photo").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            connection.outputStream.use {
                it.write(JSONObject().put("photoData", photoData).toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Não foi possível atualizar a foto (HTTP $code)" })
            }
            val json = JSONObject(body).getJSONObject("user")
            AuthUser(
                id = json.getInt("id"),
                username = json.getString("username"),
                fullName = json.optString("fullName", json.optString("full_name")),
                role = json.getString("role"),
                photoData = json.optString("photoData")
            ).also { updated -> store.save(AuthSession(token, updated), baseUrl) }
        } finally {
            connection.disconnect()
        }
    }
}
