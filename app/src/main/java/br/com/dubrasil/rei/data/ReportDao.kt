package br.com.dubrasil.rei.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports WHERE status = 'DRAFT' ORDER BY updatedAt DESC LIMIT 1")
    fun getDraft(): ReportEntity?

    @Query("SELECT * FROM reports WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getCompleted(): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE status = 'COMPLETED' AND syncStatus != 'SYNCED' ORDER BY updatedAt ASC")
    fun getPendingSync(): List<ReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(reports: List<ReportEntity>)

    @Query("DELETE FROM reports WHERE status = 'DRAFT'")
    fun deleteDraft()

    @Query("SELECT COUNT(*) FROM reports")
    fun count(): Int

    @Query("UPDATE reports SET syncStatus = :status, lastSyncAttempt = :attempt, syncError = :error WHERE dbId = :dbId")
    fun updateSyncStatus(dbId: String, status: String, attempt: Long, error: String?)
}
