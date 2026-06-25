package br.com.dubrasil.rei.data

import android.content.Context
import br.com.dubrasil.rei.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AuthClient(private val context: Context) {
    fun login(username: String, password: String): Result<AuthSession> = runCatching {
        require(BuildConfig.CENTRAL_API_URL.isNotBlank()) { "Servidor de autenticação não configurado" }
        val connection = (URL("${BuildConfig.CENTRAL_API_URL.trimEnd('/')}/api/auth/login").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 7_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val request = JSONObject().put("username", username.trim()).put("password", password).toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                error(message.ifBlank { "Não foi possível entrar (HTTP $code)" })
            }
            val json = JSONObject(body)
            val userJson = json.getJSONObject("user")
            val session = AuthSession(
                token = json.getString("token"),
                user = AuthUser(
                    id = userJson.getInt("id"),
                    username = userJson.getString("username"),
                    fullName = userJson.getString("fullName"),
                    role = userJson.getString("role")
                )
            )
            AuthStore(context).save(session)
            SyncScheduler.enqueue(context)
            session
        } finally {
            connection.disconnect()
        }
    }
}
