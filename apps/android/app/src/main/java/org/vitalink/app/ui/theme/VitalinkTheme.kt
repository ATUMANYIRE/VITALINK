package org.vitalink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vitalink.app.R

object VitalinkColors {
    val White = Color(0xFFFFFFFF)
    val Forest = Color(0xFF164C3F)
    val Pink = Color(0xFFE9A4B7)
    val Ink = Color(0xFF252B28)
    val Muted = Color(0xFF626B66)
    val Border = Color(0xFFE5E7EB)
    val RoseAccent = Color(0xFF8A4056)
    val PinkSurface = Color(0xFFFDF2F6)
    val Urgent = Color(0xFFA12D24)
    val UrgentSurface = Color(0xFFFFF1EF)
    val Review = Color(0xFF815700)
    val ReviewSurface = Color(0xFFFFF5E3)
}

@OptIn(ExperimentalTextApi::class)
val DmSans = FontFamily(
    *listOf(400, 500, 600, 700).map { weight ->
        Font(R.font.dm_sans, FontWeight(weight), variationSettings = FontVariation.Settings(
            FontVariation.weight(weight), FontVariation.Setting("opsz", 18f)
        ))
    }.toTypedArray()
)

@OptIn(ExperimentalTextApi::class)
val Fraunces = FontFamily(Font(R.font.fraunces, FontWeight.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(450),
        FontVariation.Setting("opsz", 48f), FontVariation.Setting("SOFT", 20f))))

private fun functional(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = DmSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    color = Color.Unspecified, platformStyle = PlatformTextStyle(includeFontPadding = false),
)

val EditorialDisplay = TextStyle(fontFamily = Fraunces, fontSize = 42.sp, lineHeight = 49.sp,
    letterSpacing = (-1.2).sp, color = VitalinkColors.Forest,
    platformStyle = PlatformTextStyle(includeFontPadding = false))

val ClinicalValue = functional(38, 46, FontWeight.Medium).copy(fontFeatureSettings = "tnum")

private val typography = Typography(
    displayLarge = functional(48, 56, FontWeight.Medium),
    displayMedium = functional(40, 48, FontWeight.Medium),
    displaySmall = functional(36, 44, FontWeight.Medium),
    headlineLarge = functional(32, 40, FontWeight.SemiBold),
    headlineMedium = functional(28, 36, FontWeight.SemiBold),
    headlineSmall = functional(24, 32, FontWeight.SemiBold),
    titleLarge = functional(21, 28, FontWeight.SemiBold),
    titleMedium = functional(17, 24, FontWeight.SemiBold),
    titleSmall = functional(15, 22, FontWeight.SemiBold),
    bodyLarge = functional(16, 25),
    bodyMedium = functional(14, 22),
    bodySmall = functional(12, 18),
    labelLarge = functional(16, 22, FontWeight.SemiBold),
    labelMedium = functional(13, 18, FontWeight.Medium),
    labelSmall = functional(12, 17, FontWeight.Medium),
)

@Composable
fun VitalinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VitalinkColors.Forest, onPrimary = Color.White,
            primaryContainer = VitalinkColors.White, onPrimaryContainer = VitalinkColors.Forest,
            secondary = VitalinkColors.RoseAccent, onSecondary = Color.White,
            secondaryContainer = VitalinkColors.PinkSurface, onSecondaryContainer = VitalinkColors.RoseAccent,
            tertiary = VitalinkColors.Forest, background = Color.White, onBackground = VitalinkColors.Ink,
            surface = Color.White, onSurface = VitalinkColors.Ink, surfaceVariant = Color.White,
            onSurfaceVariant = VitalinkColors.Muted, surfaceTint = Color.Transparent,
            outline = VitalinkColors.Border, outlineVariant = VitalinkColors.Border,
            error = VitalinkColors.Urgent, onError = Color.White,
            errorContainer = VitalinkColors.UrgentSurface, onErrorContainer = VitalinkColors.Urgent,
        ),
        typography = typography,
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
