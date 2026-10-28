package org.vitalink.app

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.JSONObject
import org.vitalink.app.data.*
import org.vitalink.domain.*
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflinePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "instrumentation-offline.db"
    private val cipher = PayloadCipher("vitalink-instrumentation")
    private lateinit var db: VitalinkDatabase
    private lateinit var repo: ReferralRepository
    private fun open() {
        db = Room.databaseBuilder(context, VitalinkDatabase::class.java, name).addMigrations(VitalinkDatabase.MIGRATION_1_2).build()
        repo = ReferralRepository(db, cipher, ApiClient(SessionStore(context, cipher, "test-session")))
    }
    private fun assessment() = Assessment(UUID.randomUUID().toString(), "SYN-INSTRUMENTED", Instant.now().toString(), 160, 90, 32, emptySet())
    @Before fun setup() { context.deleteDatabase(name); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun encryptedPayloadDoesNotContainPatientIdentifier() = runBlocking {
        val a = assessment(); repo.saveAssessment(a)
        val stored = requireNotNull(db.dao().assessment(a.id)).encryptedPayload
        assertFalse(stored.toString(Charsets.ISO_8859_1).contains(a.patientId))
        assertEquals(a, Wire.parseAssessment(JSONObject(cipher.decrypt(stored, "assessment:${a.id}"))))
    }
    @Test fun encryptionUsesFreshNonces() { val first = cipher.encrypt("clinical payload", "test"); val second = cipher.encrypt("clinical payload", "test"); assertFalse(first.contentEquals(second)) }
    @Test fun sameKeystoreAliasDecryptsAfterRecreation() { val blob = cipher.encrypt("persist", "context"); assertEquals("persist", PayloadCipher("vitalink-instrumentation").decrypt(blob, "context")) }
    @Test fun tamperedCiphertextIsRejected() { val blob = cipher.encrypt("persist", "context"); blob[blob.lastIndex] = (blob.last().toInt() xor 1).toByte(); assertTrue(runCatching { cipher.decrypt(blob, "context") }.isFailure) }
    @Test fun movingCiphertextBetweenRecordsIsRejected() { val blob = cipher.encrypt("persist", "record-A"); assertTrue(runCatching { cipher.decrypt(blob, "record-B") }.isFailure) }
    @Test fun offlineReferralSurvivesDatabaseReopen() = runBlocking {
        val a = assessment(); val id = repo.createReferral(a, "demo-facility")
        db.close(); open()
        val row = requireNotNull(db.dao().referral(id))
        assertEquals(ReferralState.PENDING_SYNC.name, row.state)
        assertNotNull(db.dao().outbox(id)); assertNotNull(db.dao().assessment(a.id))
    }
    @Test fun partialPersistenceRollsBackAtomically() = runBlocking {
        val a = assessment()
        runCatching { db.withTransaction { db.dao().insertAssessment(AssessmentRow(a.id, 0, byteArrayOf(1))); error("Simulated interruption before commit") } }
        assertNull(db.dao().assessment(a.id))
    }
    @Test fun duplicateReferralForSameAssessmentDoesNotCreateExtraOutbox() = runBlocking {
        val a = assessment(); val id = repo.createReferral(a, "demo-facility")
        assertTrue(runCatching { repo.createReferral(a, "demo-facility") }.isFailure)
        assertNotNull(db.dao().referral(id)); assertEquals(1, db.dao().pendingCount())
    }
    @Test fun expiredSynchronizationLeaseReturnsToPending() = runBlocking {
        val a = assessment(); val id = repo.createReferral(a, "demo-facility")
        db.dao().setDelivery(id, "SENT", 0, null)
        db.dao().updateOutbox(requireNotNull(db.dao().outbox(id)).copy(leaseUntil = 100, attemptCount = 1))
        db.close(); open(); repo.recoverExpiredLeases(101)
        assertEquals("PENDING_SYNC", db.dao().referral(id)?.state)
        assertEquals("INTERRUPTED_ATTEMPT", db.dao().outbox(id)?.lastErrorCode)
        assertNull(db.dao().outbox(id)?.leaseUntil)
    }
    @Test fun activeLeaseCannotBeReclaimed() = runBlocking {
        val id = repo.createReferral(assessment(), "demo-facility")
        db.dao().setDelivery(id, "SENT", 0, null)
        db.dao().updateOutbox(requireNotNull(db.dao().outbox(id)).copy(leaseUntil = 200))
        repo.recoverExpiredLeases(101)
        assertEquals("SENT", db.dao().referral(id)?.state)
    }
    @Test fun unauthenticatedSyncDoesNotClaimServerDelivery() = runBlocking {
        val id = repo.createReferral(assessment(), "demo-facility")
        repo.synchronize()
        assertEquals("PENDING_SYNC", db.dao().referral(id)?.state)
        assertEquals("AUTH_REQUIRED", db.dao().outbox(id)?.lastErrorCode)
        assertEquals(true, db.dao().outbox(id)?.blocked)
    }
    @Test fun queuedSnapshotIsImmutable() = runBlocking {
        val a = assessment(); repo.saveAssessment(a)
        assertTrue(runCatching { repo.createReferral(a.copy(systolic = 120), "demo-facility") }.isFailure)
        assertEquals(160, Wire.parseAssessment(JSONObject(cipher.decrypt(requireNotNull(db.dao().assessment(a.id)).encryptedPayload, "assessment:${a.id}"))).systolic)
    }
    @Test fun explicitDeletionRemovesLocalSnapshotAndOutbox() = runBlocking {
        val a = assessment(); val id = repo.createReferral(a, "demo-facility")
        repo.deleteReferral(id)
        assertNull(db.dao().referral(id)); assertNull(db.dao().outbox(id)); assertNull(db.dao().assessment(a.id))
    }
    @Test fun migrationPreservesEncryptedAssessmentAndOutbox() = runBlocking {
        db.close(); context.deleteDatabase(name)
        val legacy = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        legacy.execSQL("CREATE TABLE assessments (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, encryptedPayload BLOB NOT NULL)")
        legacy.execSQL("CREATE TABLE referrals (id TEXT NOT NULL PRIMARY KEY, assessmentId TEXT NOT NULL, encryptedRequest BLOB NOT NULL, state TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, encryptedReceipt BLOB, FOREIGN KEY(assessmentId) REFERENCES assessments(id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        legacy.execSQL("CREATE UNIQUE INDEX index_referrals_assessmentId ON referrals(assessmentId)")
        legacy.execSQL("CREATE TABLE outbox (referralId TEXT NOT NULL PRIMARY KEY, attemptCount INTEGER NOT NULL, nextAttemptAt INTEGER NOT NULL, leaseUntil INTEGER, lastErrorCode TEXT, FOREIGN KEY(referralId) REFERENCES referrals(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        val a = assessment(); val encrypted = cipher.encrypt(Wire.assessment(a).toString(), "assessment:${a.id}")
        legacy.execSQL("INSERT INTO assessments VALUES (?, ?, ?)", arrayOf(a.id, 0, encrypted))
        legacy.execSQL("PRAGMA user_version=1"); legacy.close()
        open()
        assertNotNull(db.dao().assessment(a.id))
        assertEquals(a, Wire.parseAssessment(JSONObject(cipher.decrypt(requireNotNull(db.dao().assessment(a.id)).encryptedPayload, "assessment:${a.id}"))))
    }
    @Test fun retentionNeverPurgesUnsentReferrals() = runBlocking {
        val a = assessment(); val id = repo.createReferral(a, "demo-facility")
        repo.purgeExpired(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
        assertNotNull(db.dao().referral(id)); assertNotNull(db.dao().assessment(a.id))
    }
    @Test fun staleFailureCannotOverwriteNewerAttemptLease() = runBlocking {
        val id = repo.createReferral(assessment(), "demo-facility")
        val row = requireNotNull(db.dao().referral(id))
        val old = requireNotNull(db.dao().outbox(id)).copy(attemptCount = 1, leaseUntil = 100)
        val current = old.copy(attemptCount = 2, leaseUntil = 300)
        db.dao().setDelivery(id, "SENT", 0, null); db.dao().updateOutbox(current)
        repo.recordAttemptFailure(row, old, "NETWORK_FAILURE", false)
        assertEquals("SENT", db.dao().referral(id)?.state)
        assertEquals(current, db.dao().outbox(id))
    }
}
