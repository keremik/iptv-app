package com.keremik.radio.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.keremik.radio.R
import com.keremik.radio.data.Station
import kotlin.math.abs

/** Maximale Inhaltsbreite (Tablets, Fold-Innendisplay). */
val MaxWidth = 720.dp

private val avatarColors = listOf(
  Color(0xFFC8102E), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF6A1B9A),
  Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFF5D4037), Color(0xFFAD1457),
)

/** Senderlogo auf weißem Feld; ohne Logo ein farbiges Feld mit den Anfangsbuchstaben. */
@Composable
fun StationLogo(name: String, url: String, size: Dp, shape: Shape = RoundedCornerShape(size * 0.25f)) {
  var ok by remember(url) { mutableStateOf(false) }
  val color = avatarColors[abs(name.hashCode()) % avatarColors.size]
  Box(Modifier.size(size).clip(shape).background(if (ok) Color.White else color), contentAlignment = Alignment.Center) {
    if (!ok) {
      Text(name.trim().take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold,
        fontSize = (size.value * 0.32f).sp, maxLines = 1)
    }
    if (url.isNotBlank()) {
      AsyncImage(
        model = url, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(size * 0.06f),
        onState = { ok = it is AsyncImagePainter.State.Success },
      )
    }
  }
}

/** Kleine springende Balken: dieser Sender läuft. */
@Composable
fun Equalizer(playing: Boolean, color: Color = Ui.accent, modifier: Modifier = Modifier) {
  val t = rememberInfiniteTransition(label = "eq")
  val bars = listOf(420, 300, 520).map { d -> t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d), RepeatMode.Reverse), label = "bar") }
  Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
    bars.forEachIndexed { i, a ->
      val h = if (playing) a.value else listOf(0.4f, 0.7f, 0.5f)[i]
      Box(Modifier.width(4.dp).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(color))
    }
  }
}

/** Große Überschrift oben links, rechts Aktionen; optional Zurück-Pfeil. */
@Composable
fun PageHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().statusBarsPadding()
      .padding(start = if (onBack != null) 8.dp else 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Ui.text) }
      Spacer(Modifier.width(4.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = if (onBack != null) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        else MaterialTheme.typography.displaySmall, color = Ui.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Ui.muted)
    }
    actions()
  }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp), color = Ui.text,
    modifier = modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(start = 28.dp, end = 24.dp, top = 22.dp, bottom = 14.dp))
}

/** Dunkle, abgerundete Karte. */
@Composable
fun DarkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  Box(
    modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(28.dp))
      .background(Ui.card).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
  ) { content() }
}

/** Senderkarte wie im Vorbild: Logo, Name, Herz. Der laufende Sender bekommt einen weißen Rahmen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationCard(
  station: Station,
  current: Boolean,
  playing: Boolean,
  favorite: Boolean,
  subtitle: String?,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onFavorite: () -> Unit,
  trailing: (@Composable RowScope.() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(30.dp)
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)
      .clip(shape).background(Ui.card)
      .then(if (current) Modifier.border(2.dp, Color.White.copy(alpha = 0.92f), shape) else Modifier)
      .combinedClickable(onClick = onClick, onLongClick = onLongClick)
      .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StationLogo(station.name, station.favicon, 66.dp, RoundedCornerShape(16.dp))
    Spacer(Modifier.width(18.dp))
    Column(Modifier.weight(1f)) {
      Text(station.name, style = MaterialTheme.typography.titleLarge, color = Ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (!subtitle.isNullOrBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (current) { Equalizer(playing, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
          Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = if (current) Ui.accent else Ui.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
    if (trailing != null) trailing() else {
      IconButton(onClick = onFavorite, modifier = Modifier.size(56.dp)) {
        Icon(
          if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          stringResource(if (favorite) R.string.remove_fav else R.string.add_fav),
          tint = if (favorite) Ui.heart else Ui.muted, modifier = Modifier.size(36.dp),
        )
      }
    }
  }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
  val focus = LocalFocusManager.current
  TextField(
    value = value, onValueChange = onChange,
    modifier = Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
    placeholder = { Text(stringResource(R.string.search_hint), color = Ui.muted) },
    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Ui.muted) },
    trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Ui.muted) } },
    singleLine = true, shape = CircleShape,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = Ui.card, unfocusedContainerColor = Ui.card,
      focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
      focusedTextColor = Ui.text, unfocusedTextColor = Ui.text, cursorColor = Ui.accent,
    ),
  )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 28.sp), color = Ui.muted,
    modifier = modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp))
}
