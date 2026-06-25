package br.com.dubrasil.rei.data

import android.content.Context

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

    fun save(session: AuthSession) {
        prefs.edit()
            .putString("token", session.token)
            .putInt("user_id", session.user.id)
            .putString("username", session.user.username)
            .putString("full_name", session.user.fullName)
            .putString("role", session.user.role)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
