package br.com.dubrasil.rei.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Consultas locais. A fila de sincronização é definida exclusivamente por [getPendingSync]. */
@Dao
interface ReportDao {
    @Query("SELECT * FROM reports WHERE status = 'DRAFT' ORDER BY updatedAt DESC LIMIT 1")
    fun getDraft(): ReportEntity?

    @Query("SELECT * FROM reports WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompleted(): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE status = 'COMPLETED' AND reportId = :reportId LIMIT 1")
    fun getCompletedByReportId(reportId: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE status = 'COMPLETED' AND syncStatus != 'SYNCED' ORDER BY updatedAt ASC")
    fun getPendingSync(): List<ReportEntity>

    @Query("SELECT COUNT(*) FROM reports WHERE status = 'COMPLETED' AND syncStatus != 'SYNCED'")
    fun countPendingSync(): Int

    @Query("SELECT MAX(lastSyncAttempt) FROM reports WHERE status = 'COMPLETED'")
    fun latestSyncAttempt(): Long?

    @Query("SELECT syncError FROM reports WHERE status = 'COMPLETED' AND syncError IS NOT NULL AND syncError != '' ORDER BY COALESCE(lastSyncAttempt, updatedAt) DESC LIMIT 1")
    fun latestSyncError(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(reports: List<ReportEntity>)

    @Transaction
    fun upsertHistoryAndDraft(history: ReportEntity, draft: ReportEntity) {
        upsert(history)
        upsert(draft)
    }

    @Transaction
    fun upsertHistoryAndDeleteDraft(history: ReportEntity) {
        upsert(history)
        deleteDraft()
    }

    @Query("DELETE FROM reports WHERE status = 'DRAFT'")
    fun deleteDraft()

    @Query("SELECT COUNT(*) FROM reports")
    fun count(): Int

    @Query("UPDATE reports SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE dbId = :dbId")
    fun updateSyncStatus(dbId: String, status: String, attempt: Long, error: String?)
}
