package br.com.dubrasil.rei.data

data class SyncDiagnostic(
    val serverUrl: String,
    val serverConfigured: Boolean,
    val username: String?,
    val userAuthenticated: Boolean,
    val lastAttempt: Long?,
    val pendingCount: Int,
    val lastError: String?
)

data class SyncRunResult(
    val diagnostic: SyncDiagnostic,
    val attemptError: String?
)

data class DeviceSyncStatus(
    val username: String,
    val deviceId: String,
    val appVersion: String,
    val lastSeen: String,
    val pendingCount: Int,
    val lastError: String?
)
