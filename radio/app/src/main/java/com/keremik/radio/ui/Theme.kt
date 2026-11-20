package com.keremik.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Farben: dunkler Petrol-Verlauf, dunkle Karten, weiße Schrift, rote Herzen. */
object Ui {
  val bgTop = Color(0xFF0F3F39)
  val bgMid = Color(0xFF082A26)
  val bgBottom = Color(0xFF031311)
  val card = Color(0xFF04201C)
  val cardHigh = Color(0xFF0B332E)
  val bar = Color(0xFF021412)
  val text = Color(0xFFF2F7F6)
  val muted = Color(0xFFA3BFBA)
  val heart = Color(0xFFEF3B36)
  val accent = Color(0xFF3FD0BF)
  val youtube = Color(0xFFFF0033)
  val line = Color(0x26FFFFFF)
  val pill = Color(0x2EFFFFFF)
}

private val Scheme = darkColorScheme(
  primary = Ui.accent, onPrimary = Color(0xFF00201C),
  primaryContainer = Ui.cardHigh, onPrimaryContainer = Ui.text,
  secondary = Ui.accent, secondaryContainer = Ui.cardHigh, onSecondaryContainer = Ui.text,
  background = Ui.bgMid, onBackground = Ui.text,
  surface = Ui.card, onSurface = Ui.text, onSurfaceVariant = Ui.muted,
  surfaceContainer = Ui.card, surfaceContainerHigh = Color(0xFF0A2C28), surfaceContainerHighest = Ui.cardHigh,
  surfaceContainerLow = Ui.card, surfaceContainerLowest = Ui.bgBottom,
  outline = Color(0x55FFFFFF), outlineVariant = Ui.line,
  error = Color(0xFFFF8A80),
)

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
  val base = Typography()
  val type = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 38.sp, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
  )
  MaterialTheme(colorScheme = Scheme, typography = type) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ui.bgTop, Ui.bgMid, Ui.bgBottom)))) { content() }
  }
}
