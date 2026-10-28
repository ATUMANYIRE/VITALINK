package org.vitalink.app.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "assessments")
data class AssessmentRow(@PrimaryKey val id: String, val createdAt: Long, val encryptedPayload: ByteArray)

@Entity(tableName = "referrals", foreignKeys = [ForeignKey(entity = AssessmentRow::class, parentColumns = ["id"], childColumns = ["assessmentId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("assessmentId", unique = true)])
data class ReferralRow(
    @PrimaryKey val id: String, val assessmentId: String, val encryptedRequest: ByteArray,
    val state: String, val createdAt: Long, val updatedAt: Long,
    val encryptedReceipt: ByteArray? = null,
)

@Entity(tableName = "outbox", foreignKeys = [ForeignKey(entity = ReferralRow::class, parentColumns = ["id"], childColumns = ["referralId"], onDelete = ForeignKey.CASCADE)])
data class OutboxRow(
    @PrimaryKey val referralId: String, val attemptCount: Int = 0, val nextAttemptAt: Long,
    val leaseUntil: Long? = null, val lastErrorCode: String? = null, val blocked: Boolean = false,
)

@Dao
interface VitalinkDao {
    @Insert suspend fun insertAssessment(row: AssessmentRow)
    @Insert suspend fun insertReferral(row: ReferralRow)
    @Insert suspend fun insertOutbox(row: OutboxRow)
    @Query("SELECT * FROM assessments ORDER BY createdAt DESC") fun observeAssessments(): Flow<List<AssessmentRow>>
    @Query("SELECT * FROM referrals ORDER BY createdAt DESC") fun observeReferrals(): Flow<List<ReferralRow>>
    @Query("SELECT * FROM outbox") fun observeOutbox(): Flow<List<OutboxRow>>
    @Query("SELECT * FROM assessments WHERE id = :id") suspend fun assessment(id: String): AssessmentRow?
    @Query("SELECT * FROM referrals WHERE id = :id") suspend fun referral(id: String): ReferralRow?
    @Query("SELECT * FROM outbox WHERE referralId = :id") suspend fun outbox(id: String): OutboxRow?
    @Query("SELECT * FROM outbox WHERE blocked = 0 AND leaseUntil IS NULL AND nextAttemptAt <= :now ORDER BY nextAttemptAt LIMIT 1") suspend fun nextReady(now: Long): OutboxRow?
    @Query("SELECT * FROM outbox WHERE leaseUntil IS NOT NULL AND leaseUntil <= :now") suspend fun expired(now: Long): List<OutboxRow>
    @Query("SELECT COUNT(*) FROM outbox WHERE blocked = 0") suspend fun pendingCount(): Int
    @Query("SELECT * FROM referrals WHERE state IN ('SERVER_RECEIVED','ACKNOWLEDGED')") suspend fun delivered(): List<ReferralRow>
    @Query("UPDATE referrals SET state=:state, updatedAt=:now, encryptedReceipt=:receipt WHERE id=:id") suspend fun setDelivery(id: String, state: String, now: Long, receipt: ByteArray?)
    @Update suspend fun updateOutbox(row: OutboxRow)
    @Query("DELETE FROM outbox WHERE referralId=:id") suspend fun deleteOutbox(id: String)
    @Query("UPDATE outbox SET blocked=0, nextAttemptAt=:now, lastErrorCode=NULL WHERE blocked=1 AND lastErrorCode IN ('AUTH_REQUIRED','HTTP_401','HTTP_403')") suspend fun unblockAuthentication(now: Long)
    @Query("DELETE FROM referrals WHERE id=:id") suspend fun deleteReferral(id: String)
    @Query("SELECT * FROM referrals WHERE state='CLOSED' AND updatedAt < :cutoff") suspend fun expiredClosed(cutoff: Long): List<ReferralRow>
    @Query("DELETE FROM assessments WHERE id=:id AND NOT EXISTS (SELECT 1 FROM referrals WHERE assessmentId=:id)") suspend fun deleteUnreferencedAssessment(id: String)
    @Query("DELETE FROM assessments WHERE createdAt < :cutoff AND NOT EXISTS (SELECT 1 FROM referrals WHERE assessmentId=assessments.id)") suspend fun deleteOldAssessments(cutoff: Long)
    @Query("DELETE FROM outbox") suspend fun clearOutbox()
    @Query("DELETE FROM referrals") suspend fun clearReferrals()
    @Query("DELETE FROM assessments") suspend fun clearAssessments()
}

@Database(entities = [AssessmentRow::class, ReferralRow::class, OutboxRow::class], version = 2, exportSchema = true)
abstract class VitalinkDatabase : RoomDatabase() {
    abstract fun dao(): VitalinkDao
    companion object {
        /** v1 already encrypted payloads; v2 adds durable error classification. No destructive fallback. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
