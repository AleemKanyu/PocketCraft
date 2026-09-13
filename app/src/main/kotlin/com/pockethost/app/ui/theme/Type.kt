package com.pockethost.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pockethost.app.R

val Outfit = FontFamily(
    Font(R.font.outfit_regular, weight = FontWeight.Normal),
    Font(R.font.outfit_medium, weight = FontWeight.Medium),
    Font(R.font.outfit_semibold, weight = FontWeight.SemiBold),
    Font(R.font.outfit_bold, weight = FontWeight.Bold),
    Font(R.font.outfit_extrabold, weight = FontWeight.ExtraBold)
)

/** Monocraft pixel font for UI gaming accents and headers. */
val Monocraft = FontFamily(
    Font(R.font.monocraft, weight = FontWeight.Normal)
)

/** Playfair Display — headings (serif fallback on device). */
val PlayfairDisplay = Outfit

/** DM Sans — body copy (bundled Plus Jakarta as close stand-in). */
val DMSans = Outfit

/** DM Mono — uppercase labels and console output. */
val DMMono = FontFamily.Monospace

/** Convenience alias used by button components. */
val ButtonFont = Outfit

/**
 * Fixed-width digits for numbers that change while on screen.
 *
 * Outfit's default figures are proportional, so a counter ticking 9% -> 10% -> 100% changes width
 * on almost every update and shoves whatever sits next to it — progress labels, RAM readouts and
 * player counts all visibly twitch. "tnum" makes every digit occupy the same advance width, so the
 * number updates in place. Apply it to live values, not to prose.
 */
@Composable
fun tabularNums(base: TextStyle = LocalTextStyle.current): TextStyle =
    base.copy(fontFeatureSettings = "tnum")

val PocketCraftTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DMSans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = DMMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontFamily = DMMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DMMono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.5.sp
    )
)
