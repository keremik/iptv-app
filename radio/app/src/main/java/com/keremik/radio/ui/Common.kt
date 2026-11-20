package com.keremik.radio.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlin.math.abs

private val avatarColors = listOf(
  Color(0xFFC8102E), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
  Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFF4E342E), Color(0xFFAD1457),
)

/** Senderlogo; ohne Logo (oder wenn es nicht lädt) ein farbiges Feld mit dem Anfangsbuchstaben. */
@Composable
fun StationLogo(name: String, url: String, size: Dp, shape: Shape = RoundedCornerShape(size * 0.28f)) {
  var ok by remember(url) { mutableStateOf(false) }
  val color = avatarColors[abs(name.hashCode()) % avatarColors.size]
  Box(
    Modifier.size(size).clip(shape).background(if (ok) Color.White else color),
    contentAlignment = Alignment.Center,
  ) {
    if (!ok) {
      Text(
        name.trim().take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold,
        fontSize = (size.value * 0.34f).sp, maxLines = 1,
      )
    }
    if (url.isNotBlank()) {
      AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
        onState = { ok = it is AsyncImagePainter.State.Success },
      )
    }
  }
}

/** Kleine springende Balken als Zeichen, dass ein Sender läuft. */
@Composable
fun Equalizer(playing: Boolean, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
  val t = rememberInfiniteTransition(label = "eq")
  val bars = listOf(420, 300, 520).map { d ->
    t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "bar")
  }
  Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
    bars.forEachIndexed { i, a ->
      val h = if (playing) a.value else listOf(0.4f, 0.7f, 0.5f)[i]
      Box(Modifier.width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(color))
    }
  }
}

/** Form für Einträge einer Kartengruppe (oben/unten abgerundet, wie in One UI). */
fun groupShape(index: Int, count: Int, radius: Dp = 26.dp): Shape {
  val top = if (index == 0) radius else 0.dp
  val bottom = if (index == count - 1) radius else 0.dp
  return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

