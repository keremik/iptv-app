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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keremik.radio.R
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Song

private val YouTubeRed = Color(0xFFFF0033)

/** Registerkarte Musikerkennung. [onRecognize] fragt bei Bedarf die Mikrofon-Erlaubnis an. */
@Composable
fun RecognizeScreen(vm: RadioViewModel, onRecognize: () -> Unit) {
  var settings by remember { mutableStateOf(false) }
  val history by Prefs.history.collectAsStateWithLifecycle()
  val n = vm.now
  val rec = vm.rec

  StationPage(
    title = stringResource(R.string.rec_title),
    subtitle = null,
    actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) } },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      item {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          // Song laut Sender (ICY-Daten) – sofort, ohne Erkennung.
          if (n.songTitle != null && n.station != null) {
            Card {
              Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                StationLogo(n.station.name, n.station.favicon, 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                  Text(stringResource(R.string.rec_from_radio), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                  Text(n.songTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(n.songArtist ?: n.station.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.openNowPlayingOnYouTube() }) {
                  Icon(Icons.Rounded.SmartDisplay, stringResource(R.string.rec_open_youtube), tint = YouTubeRed, modifier = Modifier.size(30.dp))
                }
              }
            }
            Spacer(Modifier.height(12.dp))
          }

          Spacer(Modifier.height(20.dp))
          BigButton(rec, fromStream = n.isPlaying, onClick = {
            if (rec is RecState.Listening || rec is RecState.Identifying) vm.cancelRecognize() else onRecognize()
          })
          Spacer(Modifier.height(20.dp))

          val status = when (rec) {
            is RecState.Listening -> stringResource(R.string.rec_listening)
            RecState.Identifying -> stringResource(R.string.rec_identifying)
            is RecState.Message -> if (rec.arg != null) stringResource(rec.text, rec.arg) else stringResource(rec.text)
            else -> stringResource(R.string.rec_button)
          }
          Text(status, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (n.isPlaying) Icons.Rounded.Radio else Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
              stringResource(if (n.isPlaying) R.string.rec_hint_stream else R.string.rec_hint_mic),
              style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
          }
          Spacer(Modifier.height(24.dp))

          if (rec is RecState.Result) {
            ResultCard(rec.song) { vm.openYouTube(rec.song.query) }
            Spacer(Modifier.height(24.dp))
          }
        }
      }

      if (history.isNotEmpty()) {
        item {
          Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 28.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rec_history), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { Prefs.clearHistory() }) { Text(stringResource(R.string.rec_history_clear)) }
          }
        }
        itemsIndexed(history, key = { _, s -> "${s.artist}|${s.title}" }) { i, s ->
          Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp).clip(groupShape(i, history.size))
            .background(Ui.card).clickable { vm.openYouTube(s.query) }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
              StationLogo(s.title, s.artwork, 44.dp)
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Icon(Icons.Rounded.SmartDisplay, stringResource(R.string.rec_open_youtube), tint = YouTubeRed)
            }
            if (i < history.size - 1) HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    }
  }

  if (settings) SettingsDialog { settings = false }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Ui.card)) { content() }
}

@Composable
private fun BigButton(rec: RecState, fromStream: Boolean, onClick: () -> Unit) {
  val active = rec is RecState.Listening || rec is RecState.Identifying
  val t = rememberInfiniteTransition(label = "pulse")
  val pulse by t.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
  val primary = MaterialTheme.colorScheme.primary
  Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
    if (active) {
      Box(Modifier.size(190.dp).scale(pulse).clip(CircleShape).background(primary.copy(alpha = 0.15f)))
      Box(Modifier.size(170.dp).scale((pulse - 1f) * 0.5f + 1f).clip(CircleShape).background(primary.copy(alpha = 0.18f)))
    }
    Box(
      Modifier.size(156.dp).clip(CircleShape).background(primary).clickable(onClick = onClick),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        if (fromStream) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
        stringResource(R.string.rec_button), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(72.dp),
      )
    }
    when (rec) {
      is RecState.Listening -> CircularProgressIndicator(progress = { rec.progress }, modifier = Modifier.size(176.dp), strokeWidth = 5.dp)
      RecState.Identifying -> CircularProgressIndicator(modifier = Modifier.size(176.dp), strokeWidth = 5.dp)
      else -> {}
    }
  }
}

@Composable
private fun ResultCard(song: Song, onYouTube: () -> Unit) {
  Card {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      StationLogo(song.title, song.artwork, 160.dp, RoundedCornerShape(28.dp))
      Spacer(Modifier.height(16.dp))
      Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
      Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      if (song.album.isNotBlank()) {
        Text(song.album, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
      }
      Spacer(Modifier.height(16.dp))
      Button(onClick = onYouTube, colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.Rounded.SmartDisplay, null); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.rec_open_youtube))
      }
    }
  }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit) {
  var token by remember { mutableStateOf(Prefs.auddToken) }
  var auto by remember { mutableStateOf(Prefs.autoYoutube) }
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(stringResource(R.string.settings)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(stringResource(R.string.auto_youtube), Modifier.weight(1f))
          Switch(checked = auto, onCheckedChange = { auto = it })
        }
        OutlinedTextField(
          value = token, onValueChange = { token = it }, singleLine = true,
          label = { Text(stringResource(R.string.audd_token_label)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.audd_token_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(stringResource(R.string.car_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    },
    confirmButton = { TextButton(onClick = { Prefs.auddToken = token; Prefs.autoYoutube = auto; onClose() }) { Text(stringResource(R.string.save)) } },
    dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
  )
}
