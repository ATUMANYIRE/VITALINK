package org.vitalink.app

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.vitalink.domain.*
import java.time.Instant
import java.util.UUID

/** Host runs separate stages with adb force-stop between them. Credentials supplied only as runner args. */
class ReferralLifecycleDeviceTest {
    @Test fun executeLifecycleStage() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val stage = args.getString("lifecycleStage")
        assumeTrue("Requires a host-coordinated real API lifecycle run", stage != null)
        val app = ApplicationProvider.getApplicationContext<VitalinkApplication>()
        val repo = app.repository
        val prefs = app.getSharedPreferences("lifecycle-evidence", 0)
        val dao = app.database.dao()
        if (stage == "offline-save") {
            assertEquals("Enable airplane mode on the host before this stage", 1, Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0))
            app.sessionStore.clear()
            val a = Assessment(UUID.randomUUID().toString(), "SYN-DEVICE-DEMO", Instant.now().toString(), 160, 90, 32, emptySet())
            assertEquals(Urgency.URGENT, ClinicalRules.assess(a).urgency)
            val id = repo.createReferral(a, args.getString("facilityId") ?: "demo-facility")
            assertTrue(prefs.edit().putString("referralId", id).commit())
            assertEquals("PENDING_SYNC", dao.referral(id)?.state)
            println("VITALINK_REFERRAL_ID=$id")
        } else {
            val id = requireNotNull(prefs.getString("referralId", null)) { "Run offline-save first" }
            when (stage) {
                "restart-verify" -> {
                    assertEquals("PENDING_SYNC", dao.referral(id)?.state)
                    assertNotNull(dao.outbox(id))
                }
                "interrupted-sync" -> {
                    val o = requireNotNull(dao.outbox(id))
                    dao.setDelivery(id, "SENT", System.currentTimeMillis(), null)
                    dao.updateOutbox(o.copy(attemptCount = 1, leaseUntil = System.currentTimeMillis() - 1))
                    assertEquals("SENT", dao.referral(id)?.state)
                }
                "recovery-verify" -> {
                    repo.recoverExpiredLeases()
                    assertEquals("PENDING_SYNC", dao.referral(id)?.state)
                    assertEquals("INTERRUPTED_ATTEMPT", dao.outbox(id)?.lastErrorCode)
                }
                "submit" -> {
                    repo.api.login(requireNotNull(args.getString("workerUsername")), requireNotNull(args.getString("workerPassword")))
                    repo.unblockAfterLogin()
                    repo.synchronize()
                    assertEquals("SERVER_RECEIVED", dao.referral(id)?.state)
                    assertNull(dao.outbox(id))
                }
                "poll-ack" -> {
                    repo.synchronize()
                    assertEquals("ACKNOWLEDGED", dao.referral(id)?.state)
                    assertNotNull(dao.referral(id)?.encryptedReceipt)
                }
                else -> error("Unknown lifecycle stage")
            }
            println("VITALINK_REFERRAL_ID=$id")
            println("VITALINK_VERIFIED_STAGE=$stage")
        }
    }
}
