package org.vitalink.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vitalink.domain.*
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

data class SavedAssessment(val assessment: Assessment, val createdAt: Long)
data class SavedReferral(val id: String, val assessment: Assessment, val facilityId: String, val state: ReferralState,
    val createdAt: Long, val attemptCount: Int, val lastErrorCode: String?, val blocked: Boolean, val nextAttemptAt: Long?,
    val acknowledgedAt: String?, val acknowledgedBy: String?)
data class LocalRecords(val assessments: List<SavedAssessment> = emptyList(), val referrals: List<SavedReferral> = emptyList())

class ReferralRepository(val db: VitalinkDatabase, private val cipher: PayloadCipher, val api: ApiClient) {
    private val dao = db.dao()
    val records = combine(dao.observeAssessments(), dao.observeReferrals(), dao.observeOutbox()) { assessments, referrals, outbox ->
        val saved = assessments.associate { row -> row.id to SavedAssessment(Wire.parseAssessment(JSONObject(cipher.decrypt(row.encryptedPayload, "assessment:${row.id}"))), row.createdAt) }
        LocalRecords(saved.values.toList(), referrals.map { row ->
            val request = JSONObject(cipher.decrypt(row.encryptedRequest, "referral:${row.id}"))
            val o = outbox.find { it.referralId == row.id }
            val receipt = row.encryptedReceipt?.let { Wire.receipt(cipher.decrypt(it, "receipt:${row.id}")) }
            SavedReferral(row.id, Wire.parseAssessment(request.getJSONObject("assessment")), request.getString("facilityId"),
                ReferralState.valueOf(row.state), row.createdAt, o?.attemptCount ?: 0, o?.lastErrorCode, o?.blocked ?: false,
                o?.nextAttemptAt, receipt?.acknowledgedAt, receipt?.acknowledgedBy)
        })
    }.flowOn(Dispatchers.IO)

    suspend fun saveAssessment(a: Assessment) = withContext(Dispatchers.IO) {
        require(AssessmentValidation.errors(a).isEmpty()) { "Correct invalid assessment inputs before saving." }
        dao.insertAssessment(AssessmentRow(a.id, System.currentTimeMillis(), cipher.encrypt(Wire.assessment(a).toString(), "assessment:${a.id}")))
    }

    /** Save assessment+immutable referral+outbox atomically, or nothing. */
    suspend fun createReferral(a: Assessment, facilityId: String): String = withContext(Dispatchers.IO) {
        require(AssessmentValidation.errors(a).isEmpty()) { "Correct invalid assessment inputs before referral creation." }
        require(Regex("[A-Za-z0-9-]{1,64}").matches(facilityId)) { "Invalid facility identifier" }
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val request = cipher.encrypt(Wire.request(id, facilityId, a), "referral:$id")
        db.withTransaction {
            val existing = dao.assessment(a.id)
            if (existing == null) dao.insertAssessment(AssessmentRow(a.id, now, cipher.encrypt(Wire.assessment(a).toString(), "assessment:${a.id}")))
            else require(Wire.parseAssessment(JSONObject(cipher.decrypt(existing.encryptedPayload, "assessment:${a.id}"))) == a) { "Assessment snapshot is immutable" }
            dao.insertReferral(ReferralRow(id, a.id, request, ReferralState.PENDING_SYNC.name, now, now))
            dao.insertOutbox(OutboxRow(id, nextAttemptAt = now))
        }
        id
    }

    suspend fun recoverExpiredLeases(now: Long = System.currentTimeMillis()) = db.withTransaction {
        dao.expired(now).forEach { o ->
            val row = dao.referral(o.referralId) ?: return@forEach
            ReferralTransitions.requireAllowed(ReferralState.valueOf(row.state), ReferralState.PENDING_SYNC)
            dao.setDelivery(row.id, ReferralState.PENDING_SYNC.name, now, row.encryptedReceipt)
            dao.updateOutbox(o.copy(leaseUntil = null, nextAttemptAt = now, lastErrorCode = "INTERRUPTED_ATTEMPT"))
        }
    }

    private suspend fun claim(now: Long): Pair<ReferralRow, OutboxRow>? = db.withTransaction {
        val o = dao.nextReady(now) ?: return@withTransaction null
        val row = dao.referral(o.referralId) ?: return@withTransaction null
        ReferralTransitions.requireAllowed(ReferralState.valueOf(row.state), ReferralState.SENT)
        val attempt = o.copy(attemptCount = o.attemptCount + 1, leaseUntil = now + RetryPolicy.LEASE_MILLIS)
        dao.updateOutbox(attempt)
        dao.setDelivery(row.id, ReferralState.SENT.name, now, row.encryptedReceipt)
        row.copy(state = ReferralState.SENT.name) to attempt
    }

    suspend fun synchronize(): Boolean = withContext(Dispatchers.IO) {
        recoverExpiredLeases()
        val session = api.sessions.read()
        for (batch in 0 until 50) {
            val claimed = claim(System.currentTimeMillis()) ?: break
            val (row, outbox) = claimed
            if (session == null) { recordAttemptFailure(row, outbox, "AUTH_REQUIRED", terminal = true); continue }
            try {
                val request = cipher.decrypt(row.encryptedRequest, "referral:${row.id}")
                val result = api.submit(row.id, request, session)
                if (result.code in setOf(200, 201)) applyReceipt(row, request, result.body)
                else recordAttemptFailure(row, outbox, "HTTP_${result.code}", !RetryPolicy.isRetryable(result.code))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: java.io.IOException) { recordAttemptFailure(row, outbox, "NETWORK_FAILURE", false) }
            catch (e: Exception) { recordAttemptFailure(row, outbox, "INVALID_RESPONSE", true) }
        }
        if (session != null) dao.delivered().forEach { row ->
            try {
                val result = api.status(row.id, session)
                if (result.code == 200) applyReceipt(row, cipher.decrypt(row.encryptedRequest, "referral:${row.id}"), result.body)
                // Never regress a confirmed delivery on a failed status poll.
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Retry on next scheduled or manual poll; keep confirmed state. */ }
        }
        purgeExpired()
        dao.pendingCount() > 0
    }

    private suspend fun applyReceipt(row: ReferralRow, request: String, raw: String) {
        val expected = JSONObject(request)
        val receipt = Wire.receipt(raw)
        receipt.validate(row.id, expected.getString("facilityId"), Wire.parseAssessment(expected.getJSONObject("assessment")))
        db.withTransaction {
            val current = dao.referral(row.id) ?: return@withTransaction
            ReferralTransitions.requireAllowed(ReferralState.valueOf(current.state), receipt.state)
            dao.setDelivery(row.id, receipt.state.name, System.currentTimeMillis(), cipher.encrypt(raw, "receipt:${row.id}"))
            dao.deleteOutbox(row.id)
        }
    }

    internal suspend fun recordAttemptFailure(row: ReferralRow, outbox: OutboxRow, code: String, terminal: Boolean) = db.withTransaction {
        val current = dao.referral(row.id) ?: return@withTransaction
        if (current.state != ReferralState.SENT.name) return@withTransaction
        val currentOutbox = dao.outbox(row.id) ?: return@withTransaction
        // Fence stale timeout completions after a new worker reclaims an expired attempt.
        if (currentOutbox.attemptCount != outbox.attemptCount || currentOutbox.leaseUntil != outbox.leaseUntil) return@withTransaction
        val now = System.currentTimeMillis()
        ReferralTransitions.requireAllowed(ReferralState.SENT, ReferralState.PENDING_SYNC)
        dao.setDelivery(row.id, ReferralState.PENDING_SYNC.name, now, current.encryptedReceipt)
        dao.updateOutbox(outbox.copy(leaseUntil = null, blocked = terminal, lastErrorCode = code,
            nextAttemptAt = now + RetryPolicy.delayMillis(outbox.attemptCount, Random.nextDouble())))
    }

    suspend fun unblockAfterLogin() = dao.unblockAuthentication(System.currentTimeMillis())
    suspend fun retryAfterReview(id: String) = db.withTransaction {
        val row = dao.referral(id) ?: return@withTransaction
        require(row.state == ReferralState.PENDING_SYNC.name) { "Only an unsent snapshot can be retried" }
        val outbox = dao.outbox(id) ?: return@withTransaction
        require(outbox.blocked) { "An automatic retry is already scheduled" }
        dao.updateOutbox(outbox.copy(blocked = false, nextAttemptAt = System.currentTimeMillis(), lastErrorCode = null))
    }
    suspend fun pendingCount() = dao.pendingCount()
    /** Explicit per-record deletion. Pending deletion requires UI confirmation of undelivered data loss. */
    suspend fun deleteReferral(id: String) = db.withTransaction {
        val row = dao.referral(id) ?: return@withTransaction
        require(row.state != ReferralState.SENT.name) { "Cannot delete an in-flight referral. Wait for its attempt to finish." }
        dao.deleteReferral(id)
        dao.deleteUnreferencedAssessment(row.assessmentId)
    }
    suspend fun deleteAssessment(id: String) = dao.deleteUnreferencedAssessment(id)
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) = db.withTransaction {
        val cutoff = now - 30L * 24 * 60 * 60 * 1000
        dao.expiredClosed(cutoff).forEach { row -> dao.deleteReferral(row.id); dao.deleteUnreferencedAssessment(row.assessmentId) }
        dao.deleteOldAssessments(cutoff)
    }
    suspend fun deleteAllLocalData() = db.withTransaction { dao.clearOutbox(); dao.clearReferrals(); dao.clearAssessments(); api.sessions.clear() }
}

