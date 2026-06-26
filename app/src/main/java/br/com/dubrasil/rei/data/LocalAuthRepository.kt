package br.com.dubrasil.rei.data

import android.content.Context
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class LocalAuthRepository(context: Context) {
    private val dao = ReiDatabase.getInstance(context.applicationContext).authDao()

    fun cacheSession(session: AuthSession, password: String, serverUrl: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        dao.upsert(
            CachedAuthUserEntity(
                username = session.user.username.trim().lowercase(),
                userId = session.user.id,
                fullName = session.user.fullName,
                role = session.user.role,
                passwordSalt = encode(salt),
                passwordHash = encode(hash(password, salt)),
                token = session.token,
                serverUrl = AuthStore.normalizeServerUrl(serverUrl),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun login(username: String, password: String): AuthSession? {
        val cached = dao.findByUsername(username.trim()) ?: return null
        val salt = decode(cached.passwordSalt)
        val expected = cached.passwordHash
        val actual = encode(hash(password, salt))
        if (actual != expected) return null

        return AuthSession(
            token = cached.token.ifBlank { "offline:${cached.username}:${cached.updatedAt}" },
            user = AuthUser(
                id = cached.userId,
                username = cached.username,
                fullName = cached.fullName,
                role = cached.role
            )
        )
    }

    fun serverUrl(username: String): String =
        dao.findByUsername(username.trim())?.serverUrl.orEmpty()

    private fun hash(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    companion object {
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
    }
}
