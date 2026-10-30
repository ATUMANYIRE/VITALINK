package org.vitalink.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.vitalink.domain.ReferralState
import java.io.File

/** Opt-in real UI recording. No mocked service, database writes or session injection. */
class DemoRecordingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as VitalinkApplication
    private val args get() = InstrumentationRegistry.getArguments()
    private val prefs get() = context.getSharedPreferences("demo-recording", 0)
    private val file get() = File(context.filesDir, "demo-recording.json")
    private fun metadata() = if (file.exists()) JSONObject(file.readText()) else JSONObject()
    private fun put(key: String, value: Any) {
        val data = metadata().put(key, value)
        file.writeText(data.toString(2))
    }
    private fun mark(name: String) {
        val data = metadata()
        val events = data.optJSONArray("events") ?: JSONArray()
        events.put(JSONObject().put("name", name).put("epochMs", System.currentTimeMillis()))
        file.writeText(data.put("events", events).toString(2))
        println("VITALINK_DEMO_MARK=$name")
    }
    private fun pause(ms: Long = 2200) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(ms)
    }
    private fun waitText(text: String) {
        compose.waitUntil(45_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        waitText(text)
        compose.onNodeWithText(text).performScrollTo().performClick()
        pause(1000)
    }
    private fun dismiss() {
        if (compose.onAllNodesWithText("Dismiss").fetchSemanticsNodes().isNotEmpty()) click("Dismiss")
    }
    private fun home() {
        waitText("Start assessment")
        dismiss()
        pause()
    }
    private fun referral() {
        home()
        compose.onNodeWithText("Referrals", substring = false).performClick()
        click(requireNotNull(prefs.getString("patientId", null)))
        waitText("Referral details")
        dismiss()
        pause()
    }

    @Test fun recordRealDemoPhase() {
        val phase = args.getString("demoPhase")
        assumeTrue("Host-coordinated recording only", phase in setOf("setup", "offline", "restart", "received", "ack", "privacy"))
        when (phase) {
            "setup" -> {
                try {
                    home()
                    compose.onNodeWithText("Account", substring = false).performClick()
                    click("Sign in")
                    compose.onNodeWithText("Username").performTextInput(requireNotNull(args.getString("workerUsername")))
                    compose.onNodeWithText("Password").performTextInput(requireNotNull(args.getString("workerPassword")))
                    click("Sign in to facility")
                    waitText(requireNotNull(args.getString("workerUsername")))
                    compose.waitUntil(45_000) { runBlocking { app.sessionStore.read() } != null }
                    put("workerSignedInBeforeOffline", true)
                } catch (_: Throwable) {
                    throw AssertionError("Health-worker sign-in failed; private credential details omitted.")
                }
            }
            "offline" -> {
                assertEquals(1, android.provider.Settings.Global.getInt(context.contentResolver, "airplane_mode_on", 0))
                home()
                val patient = "SYN-DEMO-${System.currentTimeMillis()}"
                mark("intake")
                click("Start assessment")
                compose.onNodeWithTag("patient-id-input").performTextInput(patient)
                compose.onNodeWithTag("gestation-input").performTextInput("32")
                pause(3000)
                mark("safety")
                click("Continue to observations")
                compose.onNodeWithTag("systolic-input").performScrollTo().performTextInput("160")
                compose.onNodeWithTag("diastolic-input").performScrollTo().performTextInput("90")
                compose.onNodeWithText("Urgent clinical assessment").assertIsDisplayed()
                pause(3500)
                click("Continue to warning signs")
                click("Severe headache")
                compose.onNodeWithText("Urgent clinical assessment").assertIsDisplayed()
                pause(2500)
                click("Review assessment")
                compose.onNodeWithText("Review the assessment").performScrollTo()
                pause(3500)
                mark("referral")
                click("Create offline referral")
                waitText("Referral details")
                dismiss()
                val records = runBlocking { app.repository.records.first { r -> r.referrals.any { it.assessment.patientId == patient } } }
                val saved = records.referrals.single { it.assessment.patientId == patient }
                assertEquals(ReferralState.PENDING_SYNC, saved.state)
                assertTrue(prefs.edit().putString("patientId", patient).putString("referralId", saved.id).commit())
                put("patientId", patient); put("referralId", saved.id)
                put("pendingSyncObserved", true); put("airplaneModeVerified", true)
                compose.onNodeWithText(saved.id).performScrollTo().assertIsDisplayed()
                pause(3500)
                click("View offline handoff summary")
                compose.onNodeWithText("Offline handoff summary").assertIsDisplayed()
                pause(4000)
                compose.onNodeWithText("Done").performClick()
                pause(1000)
                mark("offline-complete")
            }
            "restart" -> {
                referral()
                val id = requireNotNull(prefs.getString("referralId", null))
                compose.onNodeWithText(id).performScrollTo().assertIsDisplayed()
                val saved = runBlocking { app.repository.records.first().referrals.single { it.id == id } }
                assertEquals(ReferralState.PENDING_SYNC, saved.state)
                put("restartPersistenceObserved", true)
                mark("restart-verified")
                pause(4500)
            }
            "received" -> {
                referral()
                mark("connectivity-restored")
                click("Refresh delivery status")
                waitText("The server received this referral. A clinician has not yet acknowledged it.")
                dismiss()
                compose.onNodeWithText("The server received this referral. A clinician has not yet acknowledged it.")
                    .performScrollTo().assertIsDisplayed()
                val id = requireNotNull(prefs.getString("referralId", null))
                val saved = runBlocking { app.repository.records.first().referrals.single { it.id == id } }
                assertEquals(ReferralState.SERVER_RECEIVED, saved.state)
                assertNull(saved.acknowledgedAt)
                put("serverReceivedObserved", true)
                mark("server-received")
                pause(5000)
            }
            "ack" -> {
                referral()
                mark("mobile-refresh")
                click("Refresh delivery status")
                waitText("Acknowledged by")
                dismiss()
                compose.onNodeWithText("Acknowledged by").performScrollTo().assertIsDisplayed()
                val id = requireNotNull(prefs.getString("referralId", null))
                val saved = runBlocking { app.repository.records.first().referrals.single { it.id == id } }
                assertEquals(ReferralState.ACKNOWLEDGED, saved.state)
                assertFalse(saved.acknowledgedBy.isNullOrBlank())
                assertFalse(saved.acknowledgedAt.isNullOrBlank())
                put("mobileAcknowledgmentObserved", true)
                put("acknowledgedActorObserved", requireNotNull(saved.acknowledgedBy))
                put("acknowledgedAtObserved", requireNotNull(saved.acknowledgedAt))
                mark("mobile-acknowledged")
                pause(5500)
            }
            "privacy" -> {
                home()
                compose.onNodeWithText("Account", substring = false).performClick()
                compose.onNodeWithText("Rules-only research mode").performScrollTo().assertIsDisplayed()
                pause(3000)
                click("Privacy and local records")
                compose.onNodeWithText("Encrypted clinical payloads").assertIsDisplayed()
                mark("privacy")
                pause(4500)
            }
        }
        println("VITALINK_DEMO_PHASE=$phase PASS")
    }
}
