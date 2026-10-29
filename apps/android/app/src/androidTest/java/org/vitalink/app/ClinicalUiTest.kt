package org.vitalink.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ClinicalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun openObservations() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Open workspace").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Start assessment").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Open workspace").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Open workspace").performScrollTo().performClick()
        }
        compose.onNodeWithText("Start assessment").performScrollTo().performClick()
        compose.onNodeWithTag("patient-id-input").performTextInput("SYN-UI-CLINICAL")
        compose.onNodeWithText("Continue to observations").performScrollTo().performClick()
    }

    @Test fun severeSystolicDisplaysImmediateOfflineEscalation() {
        openObservations()
        compose.onNodeWithTag("systolic-input").performTextInput("160")
        compose.onNodeWithText("Urgent clinical assessment", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Do not wait for synchronization", substring = true).assertIsDisplayed()
    }
    @Test fun concerningSymptomIsNotSuppressedByNormalBp() {
        openObservations()
        compose.onNodeWithTag("systolic-input").performTextInput("120")
        compose.onNodeWithTag("diastolic-input").performTextInput("80")
        compose.onNodeWithText("Continue to warning signs").performScrollTo().performClick()
        compose.onNodeWithText("Severe headache").performScrollTo().performClick()
        compose.onNodeWithText("Urgent clinical assessment", substring = false).assertIsDisplayed()
    }
}
