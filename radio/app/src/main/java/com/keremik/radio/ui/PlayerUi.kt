package com.keremik.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keremik.radio.R
import com.keremik.radio.data.Prefs

/** Schwebender Mini-Player: Logo, Sender, Status, Play/Pause und ✕ (beenden). Antippen öffnet den großen Player. */
@Composable
fun MiniPlayer(vm: RadioViewModel, onOpen: () -> Unit) {
  val n = vm.now
  val s = n.station ?: return
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
      .clip(RoundedCornerShape(30.dp)).background(Ui.bar).clickable(onClick = onOpen)
      .padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StationLogo(s.name, s.favicon, 62.dp, RoundedCornerShape(16.dp))
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(s.name, style = MaterialTheme.typography.titleLarge, color = Ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(statusLine(n), style = MaterialTheme.typography.bodyLarge, color = Ui.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    PlayPause(n, vm, 56.dp, filled = false)
    IconButton(onClick = { vm.stop() }, modifier = Modifier.size(52.dp)) {
      Icon(Icons.Rounded.Close, stringResource(R.string.stop), tint = Ui.text, modifier = Modifier.size(32.dp))
    }
  }
}

@Composable
private fun PlayPause(n: NowPlaying, vm: RadioViewModel, size: Dp, filled: Boolean) {
  val active = n.isPlaying || n.buffering
  Box(
    Modifier.size(size).clip(CircleShape).background(if (filled) Color.White else Color.Transparent).clickable { vm.togglePlay() },
    contentAlignment = Alignment.Center,
  ) {
    if (n.buffering) CircularProgressIndicator(Modifier.size(size * 0.9f), strokeWidth = 2.dp, color = if (filled) Ui.bgMid else Color.White)
    Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (active) R.string.pause else R.string.play),
      tint = if (filled) Ui.bgMid else Color.White, modifier = Modifier.size(size * 0.66f))
  }
}

/** Schwebende Navigationsleiste. */
data class NavItem(val label: Int, val icon: ImageVector, val iconSelected: ImageVector = icon)

@Composable
fun BottomNav(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit) {
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)
      .clip(RoundedCornerShape(36.dp)).background(Ui.bar).border(1.dp, Ui.line, RoundedCornerShape(36.dp))
      .padding(horizontal = 2.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceAround,
  ) {
    items.forEachIndexed { i, it ->
      val sel = i == selected
      val label = stringResource(it.label)
      Column(
        Modifier.weight(if (label.length > 11) 1.4f else 1f).clip(RoundedCornerShape(20.dp)).clickable { onSelect(i) }.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(Modifier.clip(CircleShape).background(if (sel) Ui.pill else Color.Transparent).padding(horizontal = 16.dp, vertical = 6.dp)) {
          Icon(if (sel) it.iconSelected else it.icon, null, tint = if (sel) Color.White else Ui.muted, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (sel) Color.White else Ui.muted, fontSize = if (label.length > 11) 11.sp else 12.sp,
          fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
      }
    }
  }
}

/** Großer Player als Blatt von unten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: RadioViewModel, onDismiss: () -> Unit, onRecognize: () -> Unit) {
  val n = vm.now
  val s = n.station
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = Ui.bgMid, contentColor = Ui.text) {
    if (s == null) { Spacer(Modifier.height(200.dp)); return@ModalBottomSheet }
    val fav = favs.any { it.id == s.id }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally) {
      StationLogo(s.name, s.favicon, 230.dp, RoundedCornerShape(40.dp))
      Spacer(Modifier.height(26.dp))
      Text(s.name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(6.dp))
      Text(statusLine(n), style = MaterialTheme.typography.titleMedium, color = Ui.muted, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(30.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(onClick = { vm.toggleFavorite(s) }, modifier = Modifier.size(56.dp)) {
          Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(if (fav) R.string.remove_fav else R.string.add_fav),
            tint = if (fav) Ui.heart else Ui.text, modifier = Modifier.size(34.dp))
        }
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(60.dp)) {
          Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), tint = Ui.text, modifier = Modifier.size(42.dp))
        }
        PlayPause(n, vm, 86.dp, filled = true)
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(60.dp)) {
          Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), tint = Ui.text, modifier = Modifier.size(42.dp))
        }
        IconButton(onClick = { vm.openNowPlayingOnYouTube() }, enabled = n.songTitle != null, modifier = Modifier.size(56.dp)) {
          Icon(Icons.Rounded.SmartDisplay, stringResource(R.string.rec_open_youtube),
            tint = if (n.songTitle != null) Ui.youtube else Ui.line, modifier = Modifier.size(32.dp))
        }
      }
      Spacer(Modifier.height(26.dp))
      Row(
        Modifier.clip(CircleShape).background(Ui.card).clickable(onClick = onRecognize).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(Icons.Rounded.GraphicEq, null, tint = Ui.accent); Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.rec_button), fontWeight = FontWeight.Bold, color = Ui.text)
      }
    }
  }
}
