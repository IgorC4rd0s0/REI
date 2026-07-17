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
    val fullName: String,
    val deviceId: String,
    val appVersion: String,
    val lastSeen: String,
    val pendingCount: Int,
    val lastError: String?
)

data class SupervisorDashboardFilters(
    val implantador: String = "",
    val period: String = "90",
    val stage: String = "",
    val overdue: Boolean = false,
    val blockers: Boolean = false,
    val staleDays: String = "7"
)

data class DashboardIndicators(
    val total: Int,
    val overdue: Int,
    val stale: Int,
    val pendingEvaluations: Int,
    val blockers: Int,
    val concludedMonth: Int,
    val averageDurationDays: Double?,
    val averageScore: Double?,
    val syncErrors: Int
)

data class DashboardRecordSummary(
    val id: String,
    val client: String,
    val stageLabel: String,
    val assignedName: String,
    val deadline: String?,
    val daysStale: Int,
    val blocker: String?
)

data class DashboardWorkload(
    val username: String,
    val fullName: String,
    val active: Int,
    val overdue: Int,
    val stale: Int,
    val blockers: Int,
    val pendingEvaluations: Int,
    val concludedMonth: Int,
    val lastSync: String?,
    val pendingSync: Int,
    val syncErrors: Int
)

data class DashboardSyncError(
    val fullName: String,
    val appVersion: String,
    val lastSeen: String,
    val pendingCount: Int,
    val error: String
)

data class DashboardFilterOption(val value: String, val label: String)

data class SupervisorDashboard(
    val generatedAt: String,
    val indicators: DashboardIndicators,
    val byStage: List<Pair<String, Int>>,
    val workload: List<DashboardWorkload>,
    val overdue: List<DashboardRecordSummary>,
    val stale: List<DashboardRecordSummary>,
    val pendingEvaluations: List<DashboardRecordSummary>,
    val blockers: List<DashboardRecordSummary>,
    val syncErrors: List<DashboardSyncError>,
    val implantadores: List<DashboardFilterOption>,
    val stages: List<DashboardFilterOption>
)
