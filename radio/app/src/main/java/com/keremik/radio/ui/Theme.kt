package com.keremik.radio.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightFallback = lightColorScheme(
  primary = Color(0xFFC8102E), onPrimary = Color.White,
  primaryContainer = Color(0xFFFFDAD8), onPrimaryContainer = Color(0xFF410006),
  secondary = Color(0xFF8A5A00), secondaryContainer = Color(0xFFFFDDB0),
)
private val DarkFallback = darkColorScheme(
  primary = Color(0xFFFFB3AE), onPrimary = Color(0xFF68000F),
  primaryContainer = Color(0xFF93001B), onPrimaryContainer = Color(0xFFFFDAD8),
  secondary = Color(0xFFFFB950), secondaryContainer = Color(0xFF694300),
)

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val ctx = LocalContext.current
  // One UI übernimmt die Farbpalette des Hintergrundbilds (Material You) – die App folgt ihr.
  val scheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    dark -> DarkFallback
    else -> LightFallback
  }
  val base = Typography()
  val type = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  )
  MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

/** Seitenhintergrund und Kartenfarbe im Stil von One UI: graue Fläche, helle Karten (dunkel: Schwarz, Dunkelgrau). */
object Ui {
  val page: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) Color.Black else MaterialTheme.colorScheme.surfaceContainer

  val card: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest
}
