package com.keremik.radio.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keremik.radio.R
import com.keremik.radio.data.Prefs

@Composable
private fun statusLine(n: NowPlaying): String = when {
  n.error -> stringResource(R.string.playback_error)
  n.buffering -> stringResource(R.string.buffering)
  n.songTitle != null -> listOfNotNull(n.songArtist, n.songTitle).joinToString(" – ")
  else -> n.station?.subtitle?.ifBlank { null } ?: stringResource(R.string.live)
}

/** Leiste über der Navigation: Sender, Song, Play/Pause und Weiter. Antippen öffnet den großen Player. */
@Composable
fun MiniPlayer(vm: RadioViewModel, onOpen: () -> Unit) {
  val n = vm.now
  val s = n.station ?: return
  Row(
    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      .clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
      .clickable(onClick = onOpen).padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StationLogo(s.name, s.favicon, 44.dp)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSecondaryContainer)
      Text(statusLine(n), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
    }
    PlayPauseButton(n, vm, 48.dp, tonal = true)
    IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
  }
}

@Composable
private fun PlayPauseButton(n: NowPlaying, vm: RadioViewModel, size: androidx.compose.ui.unit.Dp, tonal: Boolean) {
  val playingOrLoading = n.isPlaying || n.buffering
  val content: @Composable () -> Unit = {
    Box(contentAlignment = Alignment.Center) {
      if (n.buffering) CircularProgressIndicator(Modifier.size(size * 0.9f), strokeWidth = 2.dp)
      Icon(
        if (playingOrLoading) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
        stringResource(if (playingOrLoading) R.string.pause else R.string.play),
        Modifier.size(size * 0.55f),
      )
    }
  }
  if (tonal) {
    FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(size), shape = CircleShape) { content() }
  } else {
    FilledIconButton(
      onClick = { vm.togglePlay() }, modifier = Modifier.size(size), shape = RoundedCornerShape(size * 0.32f),
      colors = IconButtonDefaults.filledIconButtonColors(),
    ) { content() }
  }
}

/** Großer Player als Blatt von unten (gut mit einer Hand bedienbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: RadioViewModel, onDismiss: () -> Unit, onRecognize: () -> Unit) {
  val n = vm.now
  val s = n.station
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    if (s == null) { Spacer(Modifier.height(200.dp)); return@ModalBottomSheet }
    val fav = favs.any { it.id == s.id }
    Column(
      Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      StationLogo(s.name, s.favicon, 220.dp, RoundedCornerShape(40.dp))
      Spacer(Modifier.height(24.dp))
      Text(s.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(6.dp))
      Text(statusLine(n), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(28.dp))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FilledTonalIconButton(onClick = { vm.toggleFavorite(s) }, modifier = Modifier.size(52.dp)) {
          Icon(if (fav) Icons.Rounded.Star else Icons.Rounded.StarOutline,
            stringResource(if (fav) R.string.remove_fav else R.string.add_fav))
        }
        FilledTonalIconButton(onClick = { vm.previous() }, modifier = Modifier.size(60.dp)) {
          Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(32.dp))
        }
        PlayPauseButton(n, vm, 84.dp, tonal = false)
        FilledTonalIconButton(onClick = { vm.next() }, modifier = Modifier.size(60.dp)) {
          Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(32.dp))
        }
        FilledTonalIconButton(onClick = { vm.openNowPlayingOnYouTube() }, enabled = n.songTitle != null, modifier = Modifier.size(52.dp)) {
          Icon(Icons.Rounded.SmartDisplay, stringResource(R.string.rec_open_youtube))
        }
      }
      Spacer(Modifier.height(24.dp))
      if (n.songTitle != null) {
        FilledTonalButton(onClick = { vm.openNowPlayingOnYouTube() }) {
          Icon(Icons.Rounded.SmartDisplay, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_open_youtube))
        }
      }
      TextButton(onClick = onRecognize) {
        Icon(Icons.Rounded.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_button))
      }
    }
  }
}
