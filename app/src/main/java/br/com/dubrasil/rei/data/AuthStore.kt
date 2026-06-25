package br.com.dubrasil.rei.data

import android.content.Context
import br.com.dubrasil.rei.BuildConfig

data class AuthUser(
    val id: Int,
    val username: String,
    val fullName: String,
    val role: String
) {
    val isSupervisor: Boolean get() = role == "supervisor"
}

data class AuthSession(val token: String, val user: AuthUser)

class AuthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rei_auth", Context.MODE_PRIVATE)

    fun token(): String = prefs.getString("token", "").orEmpty()
    fun serverUrl(): String = prefs.getString("server_url", BuildConfig.CENTRAL_API_URL).orEmpty()

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
        prefs.edit().clear().putString("server_url", currentServer).apply()
    }

    companion object {
        fun normalizeServerUrl(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            if (trimmed.isBlank()) return ""
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        }
    }
}
