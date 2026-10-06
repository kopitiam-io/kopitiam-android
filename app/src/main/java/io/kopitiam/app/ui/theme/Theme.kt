package io.kopitiam.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// MARK: - Warm Kopi palette (matches iOS Theme.swift + web exactly)

/** Design tokens resolved for the active color scheme. */
data class KopiColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val textSoft: Color,
    val line: Color,
    val accent: Color,
    val brand: Color,
    val onBrand: Color,
    val dark: Boolean,
)

val KopiLight = KopiColors(
    bg = Color(0xFFF7F1E8),
    surface = Color(0xFFFFFDF9),
    text = Color(0xFF1A1310),
    textSoft = Color(0xFF6B5D52),
    line = Color(0xFFE7DCCB),
    accent = Color(0xFF1F6F5C),   // jade — brand accent (was burnt-orange)
    brand = Color(0xFF6F4E37),    // warm brown — buttons/primary surfaces
    onBrand = Color(0xFFF7F1E8),
    dark = false,
)

val KopiDark = KopiColors(
    bg = Color(0xFF221E1A),
    surface = Color(0xFF2C2823),
    text = Color(0xFFF7F1E8),
    textSoft = Color(0xFFC9A876),
    line = Color(0xFF3D372F),
    accent = Color(0xFF3FA98C),   // brighter jade for dark scheme
    brand = Color(0xFFC9A876),
    onBrand = Color(0xFF221E1A),
    dark = true,
)

/** Radius scale — mirrors iOS Radius (8/14/22/pill). */
object Radius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 22.dp
    val pill = 999.dp
}

/** Spacing scale — mirrors iOS Space (6/10/16/24/36). */
object Space {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 36.dp
}

/** Access the resolved warm-kopi tokens from anywhere in the tree. */
val LocalKopi = staticCompositionLocalOf { KopiLight }

/**
 * Nyonya-tile brand signature band — a thin Peranakan diamond-lattice strip.
 * Mirrors the iOS NyonyaTileBand and the Play feature graphic: a kopi-brown
 * diamond lattice with jade dots at the crossings, closed by a jade grout line.
 * Restrained: one accent strip, never wallpaper.
 */
@Composable
fun NyonyaTileBand(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val jade = Color(0xFF1F6F5C)
    val jadeDark = Color(0xFF144E40)
    val kopi = Color(0xFFC68F52)
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val h = size.height
        val cell = h                    // one diamond per band-height square
        val stroke = h * 0.14f
        var x = 0f
        // brown diamond lattice
        while (x < size.width + cell) {
            // ascending stroke of the diamond
            drawLine(kopi, Offset(x, h), Offset(x + cell / 2, 0f), strokeWidth = stroke)
            // descending stroke
            drawLine(kopi, Offset(x + cell / 2, 0f), Offset(x + cell, h), strokeWidth = stroke)
            // mirror the lower half for the lattice
            drawLine(kopi, Offset(x, 0f), Offset(x + cell / 2, h), strokeWidth = stroke)
            drawLine(kopi, Offset(x + cell / 2, h), Offset(x + cell, 0f), strokeWidth = stroke)
            // jade dot at the crossing centre
            drawCircle(jade, radius = h * 0.14f, center = Offset(x + cell / 2, h / 2))
            x += cell
        }
        // jade grout line closing the band underneath
        drawLine(jadeDark, Offset(0f, h), Offset(size.width, h), strokeWidth = h * 0.18f)
    }
}

/**
 * True when the OS "Remove animations" accessibility setting is on. Used to
 * freeze the house-ad and press-scale motion, matching iOS reduceMotion.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(
        resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    )
    return scale == 0f
}

@Composable
fun KopitiamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val kopi = if (darkTheme) KopiDark else KopiLight

    // Map the warm-kopi tokens onto a Material3 ColorScheme so stock components
    // (buttons, sliders, pickers) inherit the palette.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = kopi.brand,
            onPrimary = kopi.onBrand,
            secondary = kopi.accent,
            onSecondary = kopi.onBrand,
            background = kopi.bg,
            onBackground = kopi.text,
            surface = kopi.surface,
            onSurface = kopi.text,
            surfaceVariant = kopi.surface,
            onSurfaceVariant = kopi.textSoft,
            outline = kopi.line,
            error = Color(0xFFE5484D),
        )
    } else {
        lightColorScheme(
            primary = kopi.brand,
            onPrimary = kopi.onBrand,
            secondary = kopi.accent,
            onSecondary = kopi.onBrand,
            background = kopi.bg,
            onBackground = kopi.text,
            surface = kopi.surface,
            onSurface = kopi.text,
            surfaceVariant = kopi.surface,
            onSurfaceVariant = kopi.textSoft,
            outline = kopi.line,
            error = Color(0xFFD1332B),
        )
    }

    CompositionLocalProvider(LocalKopi provides kopi) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}
