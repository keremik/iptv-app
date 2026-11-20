package com.keremik.radio.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keremik.radio.R
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Song

/** „Erkannte Songs“: Erkennungsknopf, Song laut Sender, Ergebnis und Verlauf. */
@Composable
fun SongsScreen(vm: RadioViewModel, onRecognize: () -> Unit) {
  val history by Prefs.history.collectAsStateWithLifecycle()
  val n = vm.now
  val rec = vm.rec
  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    item {
      PageHeader(stringResource(R.string.tab_songs)) {
        if (history.isNotEmpty()) IconButton(onClick = { Prefs.clearHistory() }) {
          Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.rec_history_clear), tint = Ui.text, modifier = Modifier.size(30.dp))
        }
      }
    }
    item {
      DarkCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          BigButton(rec, fromStream = n.isPlaying) {
            if (rec is RecState.Listening || rec is RecState.Identifying) vm.cancelRecognize() else onRecognize()
          }
          Spacer(Modifier.height(16.dp))
          val status = when (rec) {
            is RecState.Listening -> stringResource(R.string.rec_listening)
            RecState.Identifying -> stringResource(R.string.rec_identifying)
            is RecState.Message -> if (rec.arg != null) stringResource(rec.text, rec.arg) else stringResource(rec.text)
            else -> stringResource(R.string.rec_button)
          }
          Text(status, style = MaterialTheme.typography.titleLarge, color = Ui.text, textAlign = TextAlign.Center)
          Spacer(Modifier.height(6.dp))
          Text(stringResource(if (n.isPlaying) R.string.rec_hint_stream else R.string.rec_hint_mic),
            style = MaterialTheme.typography.bodyMedium, color = Ui.muted, textAlign = TextAlign.Center)
        }
      }
    }
    if (rec is RecState.Result) item {
      Spacer(Modifier.height(12.dp))
      ResultCard(rec.song) { vm.openYouTube(rec.song.query) }
    }
    if (n.songTitle != null && n.station != null) {
      item { SectionTitle(stringResource(R.string.rec_from_radio)) }
      item {
        SongCard(Song(n.songArtist ?: n.station.name, n.songTitle, artwork = n.station.favicon)) { vm.openNowPlayingOnYouTube() }
      }
    }
    item { SectionTitle(stringResource(R.string.rec_history)) }
    if (history.isEmpty()) item { HintText(stringResource(R.string.rec_history_empty)) }
    items(history, key = { "${it.artist}|${it.title}|${it.time}" }) { s -> SongCard(s) { vm.openYouTube(s.query) } }
  }
}

@Composable
private fun SongCard(s: Song, onClick: () -> Unit) {
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(30.dp))
      .background(Ui.card).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StationLogo(s.title, s.artwork, 60.dp, RoundedCornerShape(14.dp))
    Spacer(Modifier.width(16.dp))
    Column(Modifier.weight(1f)) {
      Text(s.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(s.artist, style = MaterialTheme.typography.bodyMedium, color = Ui.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
      Icon(Icons.Rounded.SmartDisplay, stringResource(R.string.rec_open_youtube), tint = Ui.youtube, modifier = Modifier.size(32.dp))
    }
  }
}

@Composable
private fun BigButton(rec: RecState, fromStream: Boolean, onClick: () -> Unit) {
  val active = rec is RecState.Listening || rec is RecState.Identifying
  val t = rememberInfiniteTransition(label = "pulse")
  val pulse by t.animateFloat(1f, 1.16f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
  Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
    if (active) {
      Box(Modifier.size(180.dp).scale(pulse).clip(CircleShape).background(Ui.accent.copy(alpha = 0.14f)))
      Box(Modifier.size(162.dp).scale((pulse - 1f) * 0.5f + 1f).clip(CircleShape).background(Ui.accent.copy(alpha = 0.18f)))
    }
    Box(
      Modifier.size(148.dp).clip(CircleShape)
        .background(Brush.linearGradient(listOf(Color(0xFF5BE3D2), Color(0xFF1E9E90))))
        .clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(if (fromStream) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, stringResource(R.string.rec_button),
        tint = Color(0xFF00201C), modifier = Modifier.size(70.dp))
    }
    when (rec) {
      is RecState.Listening -> CircularProgressIndicator(progress = { rec.progress }, modifier = Modifier.size(168.dp), strokeWidth = 5.dp, color = Color.White, trackColor = Ui.line)
      RecState.Identifying -> CircularProgressIndicator(modifier = Modifier.size(168.dp), strokeWidth = 5.dp, color = Color.White)
      else -> {}
    }
  }
}

@Composable
private fun ResultCard(song: Song, onYouTube: () -> Unit) {
  DarkCard {
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      StationLogo(song.title, song.artwork, 170.dp, RoundedCornerShape(28.dp))
      Spacer(Modifier.height(16.dp))
      Text(song.title, style = MaterialTheme.typography.headlineSmall, color = Ui.text, textAlign = TextAlign.Center)
      Text(song.artist, style = MaterialTheme.typography.titleMedium, color = Ui.muted, textAlign = TextAlign.Center)
      if (song.album.isNotBlank()) Text(song.album, style = MaterialTheme.typography.bodySmall, color = Ui.muted, textAlign = TextAlign.Center)
      Spacer(Modifier.height(16.dp))
      Button(onClick = onYouTube, colors = ButtonDefaults.buttonColors(containerColor = Ui.youtube, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Icon(Icons.Rounded.SmartDisplay, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.rec_open_youtube), fontWeight = FontWeight.Bold)
      }
    }
  }
}
