package org.vitalink.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.vitalink.app.data.SavedReferral
import org.vitalink.app.ui.screens.ReferralDetailScreen
import org.vitalink.app.ui.theme.VitalinkTheme
import org.vitalink.domain.*

/** Native component fixtures only; this is not persistence or real-service evidence. */
class ReferralPresentationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun render(state: ReferralState, fontScale: Float = 1f) {
        val assessment = Assessment("611147a5-4073-491d-9227-3731f1f1a102", "SYN-UI-PRESENTATION",
            "2026-10-28T05:00:00Z", 120, 80, 32, emptySet())
        val referral = SavedReferral("611147a5-4073-491d-9227-3731f1f1a101", assessment, "demo-facility",
            state, 1793163600000L, 0, null, false, null, null, null)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VitalinkTheme {
                    androidx.compose.material3.Surface(color = Color.White) {
                        Column(Modifier.widthIn(max = 320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ReferralDetailScreen(referral, false, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
        }
    }

    @Test fun pendingReferralNeverShowsConfirmedClinicalAcknowledgment() {
        render(ReferralState.PENDING_SYNC)
        compose.onNodeWithText("Saved on this device. Delivery to the receiving service is not confirmed.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Acknowledged. Not confirmed").performScrollTo().assertIsDisplayed()
    }

    @Test fun narrowLayoutAndLargeTextKeepTheHandoffActionReachable() {
        render(ReferralState.SERVER_RECEIVED, fontScale = 1.5f)
        compose.onNodeWithText("View offline handoff summary").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Refresh delivery status").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
