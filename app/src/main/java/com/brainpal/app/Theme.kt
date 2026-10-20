@file:OptIn(ExperimentalTextApi::class)

package com.brainpal.app

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/*
 * De nieuwe look van BrainPal ("De brief"): koel papier, inkt, en brein-roze alleen voor vlakken (nooit voor tekst).
 * Letters: Bricolage Grotesque (titels en regels) en Literata (lezen). Beide hebben een open licentie (SIL OFL), zie assets/.
 */

private fun bricolage(weight: Int, width: Float = 100f): Font = Font(
    R.font.bricolage_grotesque, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width))
)

private fun literata(weight: Int): Font = Font(
    R.font.literata, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Gewone stand van Bricolage Grotesque: alle tekst in de app die geen andere letter krijgt. */
val Display = FontFamily(bricolage(400), bricolage(500), bricolage(600), bricolage(700), bricolage(800))

/** Smalle stand, voor grote koppen waar veel woorden in moeten. */
val DisplayNarrow = FontFamily(bricolage(700, 85f), bricolage(800, 85f))

/** Leestekst: samenvattingen en de leesmodus. */
val Read = FontFamily(literata(400), literata(500), literata(700))

val BrainTypography: Typography = Typography().let { b ->
    b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = Display), displayMedium = b.displayMedium.copy(fontFamily = Display),
        displaySmall = b.displaySmall.copy(fontFamily = Display), headlineLarge = b.headlineLarge.copy(fontFamily = Display),
        headlineMedium = b.headlineMedium.copy(fontFamily = Display), headlineSmall = b.headlineSmall.copy(fontFamily = Display),
        titleLarge = b.titleLarge.copy(fontFamily = Display), titleMedium = b.titleMedium.copy(fontFamily = Display),
        titleSmall = b.titleSmall.copy(fontFamily = Display), bodyLarge = b.bodyLarge.copy(fontFamily = Display),
        bodyMedium = b.bodyMedium.copy(fontFamily = Display), bodySmall = b.bodySmall.copy(fontFamily = Display),
        labelLarge = b.labelLarge.copy(fontFamily = Display), labelMedium = b.labelMedium.copy(fontFamily = Display),
        labelSmall = b.labelSmall.copy(fontFamily = Display),
    )
}

val BrainLight = lightColorScheme(
    primary = Color(0xFF16213E), onPrimary = Color(0xFFF2F4F8),          // inkt op papier
    background = Color(0xFFF2F4F8), onBackground = Color(0xFF16213E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF16213E),
    surfaceVariant = Color(0xFFE4E9F2), onSurfaceVariant = Color(0xFF5B6478),
    outlineVariant = Color(0xFFD9DEE8),
    secondaryContainer = Color(0xFFFFD3DD), onSecondaryContainer = Color(0xFF16213E),
    tertiary = Color(0xFFFF7A9A), onTertiary = Color(0xFF16213E),         // brein-roze
    error = Color(0xFFC2352F),
)

val BrainDark = darkColorScheme(
    primary = Color(0xFFEDF1FA), onPrimary = Color(0xFF0F1526),
    background = Color(0xFF0F1526), onBackground = Color(0xFFEDF1FA),
    surface = Color(0xFF182038), onSurface = Color(0xFFEDF1FA),
    surfaceVariant = Color(0xFF222C48), onSurfaceVariant = Color(0xFFA3ADC4),
    outlineVariant = Color(0xFF28324E),
    secondaryContainer = Color(0xFF4A2A3C), onSecondaryContainer = Color(0xFFEDF1FA),
    tertiary = Color(0xFFFF8FA9), onTertiary = Color(0xFF0F1526),
    error = Color(0xFFFF8A84),
)

/** Het gele "nieuw"-streepje achter een titel. */
val MarkerLight = Color(0xFFFFD84D)
val MarkerDark = Color(0xFF6B5A12)

// Eén rustige kleur per onderwerp: (licht, donker). Staat als stip bij elk bericht.
private val TOPIC_COLORS: Map<String, Pair<Long, Long>> = mapOf(
    "nieuws" to (0xFF2F5BB7 to 0xFF7FA3F0), "tech" to (0xFF0E8F8A to 0xFF4FD0C9), "ai" to (0xFF7A4FD6 to 0xFFB496F5),
    "wetenschap" to (0xFF2E9A55 to 0xFF6FD097), "ruimte" to (0xFF3A3FB0 to 0xFF8E94F0), "economie" to (0xFF9A6B00 to 0xFFE0B04A),
    "sport" to (0xFFB3392F to 0xFFF08A80), "cultuur" to (0xFFB0398B to 0xFFE88CC8), "gezondheid" to (0xFF6D8F1F to 0xFFB7D76A),
    "gaming" to (0xFFD4651A to 0xFFF2A263), "opmerkelijk" to (0xFF7A6A5A to 0xFFC0B2A4),
)

fun topicColor(topicId: String, dark: Boolean): Color {
    val p = TOPIC_COLORS[topicId] ?: return Color(if (dark) 0xFFA3ADC4 else 0xFF5B6478)
    return Color(if (dark) p.second else p.first)
}

/** Schakelaars met een roze vlak als ze aan staan. */
@Composable
fun appSwitchColors(): SwitchColors {
    val cs = MaterialTheme.colorScheme
    return SwitchDefaults.colors(
        checkedTrackColor = cs.tertiary, checkedThumbColor = cs.surface, checkedBorderColor = cs.tertiary,
        uncheckedTrackColor = cs.surfaceVariant, uncheckedThumbColor = cs.onSurfaceVariant, uncheckedBorderColor = cs.outlineVariant,
    )
}

/** Het brein uit het icoon, getekend uit cirkels: twee helften met een streep ertussen. `cut` is de kleur van de achtergrond. */
@Composable
fun BrainMark(color: Color, cut: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.width / 312f
        val lobes = listOf(Triple(205f, 215f, 72f), Triple(165f, 268f, 62f), Triple(202f, 318f, 58f), Triple(238f, 266f, 76f), Triple(236f, 205f, 58f))
        for ((x, y, r) in lobes) {
            drawCircle(color, r * s, Offset((x - 100f) * s, (y - 130f) * s))
            drawCircle(color, r * s, Offset((512f - x - 100f) * s, (y - 130f) * s))
        }
        drawLine(cut, Offset((256f - 100f) * s, 10f * s), Offset((256f - 100f) * s, 215f * s), strokeWidth = 9f * s, cap = StrokeCap.Round)
    }
}
