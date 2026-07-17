package br.com.dubrasil.rei.data

import android.content.Context
import br.com.dubrasil.rei.BuildConfig
import java.util.UUID

data class AuthUser(
    val id: Int,
    val username: String,
    val fullName: String,
    val role: String
) {
    val isSupervisor: Boolean get() = role == "supervisor"
}

data class AuthSession(val token: String, val user: AuthUser)

/** Mantém somente sessão, preferências de conexão e diagnóstico; nunca armazena senha em texto. */
class AuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rei_auth", Context.MODE_PRIVATE)

    fun token(): String = prefs.getString("token", "").orEmpty()
    fun serverUrl(): String = prefs.getString("server_url", BuildConfig.CENTRAL_API_URL).orEmpty()

    fun deviceId(): String {
        prefs.getString("device_id", "")?.takeIf { it.isNotBlank() }?.let { return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).commit() }
    }

    fun beginSyncAttempt(attempt: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_sync_attempt", attempt).apply()
    }

    fun finishSyncAttempt(error: String?) {
        prefs.edit().apply {
            if (error.isNullOrBlank()) remove("last_sync_error")
            else putString("last_sync_error", error.take(500))
        }.apply()
    }

    fun lastSyncAttempt(): Long? = prefs.getLong("last_sync_attempt", 0L).takeIf { it > 0L }
    fun lastSyncError(): String? = prefs.getString("last_sync_error", null)?.takeIf { it.isNotBlank() }

    fun saveServerUrl(value: String) {
        prefs.edit().putString("server_url", normalizeServerUrl(value)).apply()
    }

    fun currentUser(): AuthUser? {
        val token = token()
        if (token.isBlank()) return null
        val username = prefs.getString("username", "").orEmpty()
        val role = prefs.getString("role", "").orEmpty()
        if (username.isBlank() || role !in setOf("supervisor", "implantador")) return null
        return AuthUser(
            prefs.getInt("user_id", 0), username,
            prefs.getString("full_name", username).orEmpty(), role
        )
    }

    fun save(session: AuthSession, serverUrl: String = serverUrl()) {
        prefs.edit()
            .putString("server_url", normalizeServerUrl(serverUrl))
            .putString("token", session.token)
            .putInt("user_id", session.user.id)
            .putString("username", session.user.username)
            .putString("full_name", session.user.fullName)
            .putString("role", session.user.role)
            .apply()
    }

    fun clear() {
        val currentServer = serverUrl()
        val currentDeviceId = deviceId()
        val attempt = lastSyncAttempt()
        val error = lastSyncError()
        prefs.edit().clear()
            .putString("server_url", currentServer)
            .putString("device_id", currentDeviceId)
            .apply {
                attempt?.let { putLong("last_sync_attempt", it) }
                error?.let { putString("last_sync_error", it) }
            }
            .apply()
    }

    companion object {
        fun normalizeServerUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }
    }
}
