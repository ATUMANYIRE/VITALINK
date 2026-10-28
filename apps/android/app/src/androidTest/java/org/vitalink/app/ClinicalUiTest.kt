package org.vitalink.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class ClinicalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun severeSystolicDisplaysImmediateOfflineEscalation() {
        compose.onNodeWithText("Systolic mmHg").performTextInput("160")
        compose.onNodeWithText("Urgent clinical assessment", substring = false).assertIsDisplayed()
        compose.onNodeWithText("Do not wait for synchronization", substring = true).assertIsDisplayed()
    }
    @Test fun concerningSymptomIsNotSuppressedByNormalBp() {
        compose.onNodeWithText("Systolic mmHg").performTextInput("120")
        compose.onNodeWithText("Diastolic mmHg").performTextInput("80")
        compose.onNodeWithText("Severe headache").performScrollTo().performClick()
        compose.onNodeWithText("Urgent clinical assessment", substring = false).performScrollTo().assertIsDisplayed()
    }
}
