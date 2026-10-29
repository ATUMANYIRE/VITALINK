package org.vitalink.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Captures real device pixels. Only explicit screenshot-mode invocations run this journey. */
@RunWith(AndroidJUnit4::class)
class NativeScreenshotJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val captured = JSONArray()

    private fun capture(name: String) {
        compose.waitForIdle()
        // Semantics can update before RenderThread presents the new window.
        // Wait for native frame callbacks before taking actual device pixels.
        val frames = CountDownLatch(1)
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            view.postOnAnimation { view.postOnAnimation { frames.countDown() } }
        }
        assertTrue("Native frame did not arrive", frames.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val prefix = InstrumentationRegistry.getArguments().getString("screenPrefix")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,40}")) } ?: "native"
        val directory = File(context.filesDir, "ui-screenshots").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Native screenshot unavailable" }
        val file = File(directory, "$prefix-$name.png")
        file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        captured.put(JSONObject().put("file", file.name).put("widthPixels", bitmap.width).put("heightPixels", bitmap.height))
        bitmap.recycle()
        File(directory, "$prefix-capture-manifest.json").writeText(JSONObject()
            .put("source", "Android UiAutomation.takeScreenshot; actual running application")
            .put("density", context.resources.displayMetrics.density)
            .put("fontScale", context.resources.configuration.fontScale)
            .put("captures", captured).toString(2))
    }

    private fun workspace() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("Open workspace").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Start assessment").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Open workspace").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Maternal care,\nconnected.").assertIsDisplayed()
            if (InstrumentationRegistry.getArguments().getString("uiCaptureMode") == "journey") {
                compose.onNodeWithText("Open workspace").assertIsDisplayed()
            }
            capture("01-welcome")
            compose.onNodeWithText("Open workspace").performScrollTo().performClick()
        }
        capture("02-home")
    }

    @Test fun captureNativeAssessmentAndReferralJourney() {
        val mode = InstrumentationRegistry.getArguments().getString("uiCaptureMode")
        org.junit.Assume.assumeTrue("Explicit UI capture invocation required", mode == "journey" || mode == "layout")
        workspace()
        compose.onNodeWithText("Start assessment").performScrollTo().performClick()
        val syntheticId = "SYN-UI-${UUID.randomUUID().toString().take(8)}"
        compose.onNodeWithTag("patient-id-input").performTextInput(syntheticId)
        compose.onNodeWithTag("gestation-input").performTextInput("32")
        capture("03-intake")
        compose.onNodeWithText("Continue to observations").performScrollTo().performClick()
        compose.onNodeWithTag("systolic-input").performScrollTo().performTextInput("160")
        compose.onNodeWithTag("diastolic-input").performScrollTo().performTextInput("90")
        compose.onNodeWithText("Urgent clinical assessment").assertIsDisplayed()
        capture("04-observations-urgent")
        compose.onNodeWithText("Continue to warning signs").performScrollTo().performClick()
        compose.onNodeWithText("Severe headache").performScrollTo().performClick()
        compose.onNodeWithText("Urgent clinical assessment").assertIsDisplayed()
        capture("05-warning-signs")
        compose.onNodeWithText("Review assessment").performScrollTo().performClick()
        compose.onNodeWithText("Review the assessment").performScrollTo()
        capture("06-review")
        if (mode == "layout") return
        compose.onNodeWithText("Create offline referral").performScrollTo().performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Referral details").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Dismiss").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Dismiss").performScrollTo().performClick()
        }
        compose.onNodeWithText("Referral details").performScrollTo()
        capture("07-referral-details")
        compose.onNodeWithText("Handoff progress").performScrollTo()
        compose.onNodeWithContentDescription("Acknowledged. Not confirmed").performScrollTo().assertIsDisplayed()
        capture("08-referral-progress")
        compose.onNodeWithText("View offline handoff summary").performScrollTo().performClick()
        compose.onNodeWithText("Offline handoff summary").assertIsDisplayed()
        capture("13-offline-handoff-summary")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        capture("09-referrals")
        compose.onNodeWithText("Records", substring = false).performClick()
        capture("10-records")
        compose.onNodeWithText(syntheticId, substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Assessment record").performScrollTo().assertIsDisplayed()
        capture("14-saved-assessment")
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Account", substring = false).performClick()
        capture("11-account")
        compose.onNodeWithText("Privacy and local records").performScrollTo().performClick()
        capture("12-privacy")
        compose.onNodeWithContentDescription("Back", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Sign in", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Connect your facility.").assertIsDisplayed()
        capture("15-sign-in")
        compose.onNodeWithText("Continue working offline").performScrollTo().performClick()
        compose.onNodeWithText("Account", substring = false).performClick()
        compose.onNodeWithText("Fonts and icon licenses").performScrollTo().performClick()
        capture("16-licenses")
    }
}
