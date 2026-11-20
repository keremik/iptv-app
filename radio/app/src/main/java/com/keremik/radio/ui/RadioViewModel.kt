package com.keremik.radio.ui

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Song
import com.keremik.radio.data.Station
import com.keremik.radio.playback.AudioTap
import com.keremik.radio.playback.Items
import com.keremik.radio.playback.PlaybackService
import com.keremik.radio.recognize.RecognizeResult
import com.keremik.radio.recognize.Recognizer
import com.keremik.radio.recognize.YouTube
import kotlinx.coroutines.Job
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Was gerade läuft – aus der MediaSession, also derselbe Stand wie im Auto und in der Benachrichtigung. */
data class NowPlaying(
  val mediaId: String = "",
  val station: Station? = null,
  val isPlaying: Boolean = false,
  val buffering: Boolean = false,
  val error: Boolean = false,
  /** Songtitel und Interpret aus dem Stream, falls der Sender sie mitschickt. */
  val songTitle: String? = null,
  val songArtist: String? = null,
) {
  val songQuery: String? get() = songTitle?.let { t -> listOfNotNull(songArtist, t).joinToString(" ") }
}

sealed interface RecState {
  data object Idle : RecState
  data class Listening(val progress: Float, val fromStream: Boolean) : RecState
  data object Identifying : RecState
  data class Result(val song: Song) : RecState
  data class Message(val text: Int, val arg: String? = null) : RecState
}

class RadioViewModel(app: Application) : AndroidViewModel(app) {

  var controller: MediaController? by mutableStateOf(null)
    private set
  var now by mutableStateOf(NowPlaying())
    private set
  var rec: RecState by mutableStateOf(RecState.Idle)
    private set
  var micLevel by mutableFloatStateOf(0f)
    private set

  private var recJob: Job? = null

  init {
    viewModelScope.launch {
      val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
      val c = MediaController.Builder(app, token).buildAsync().await()
      controller = c
      c.addListener(object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
      })
      refresh()
    }
    // Sobald die Listen geladen sind, kann der laufende Sender zugeordnet werden (z. B. nach Neustart der App).
    viewModelScope.launch {
      kotlinx.coroutines.flow.merge(Catalog.state(Catalog.DE), Catalog.state(Catalog.TR), Prefs.favorites).collect { refresh() }
    }
  }

  private fun refresh() {
    val c = controller ?: return
    val item = c.currentMediaItem
    val id = item?.mediaId.orEmpty()
    val st = Catalog.find(Items.stationId(id))
    val md: MediaMetadata = c.mediaMetadata
    val icy = md.extras?.containsKey(Items.EXTRA_ICY) == true
    val hasArtist = md.extras?.getBoolean(Items.EXTRA_ICY) == true
    now = NowPlaying(
      mediaId = id,
      station = st,
      isPlaying = c.isPlaying,
      buffering = c.playbackState == Player.STATE_BUFFERING && c.playWhenReady,
      error = c.playerError != null,
      songTitle = if (icy) md.title?.toString() else null,
      songArtist = if (icy && hasArtist) md.artist?.toString() else null,
    )
  }

  /** Spielt [station] und legt die ganze Liste [category] in die Warteschlange (für Weiter/Zurück im Auto). */
  fun play(category: String, list: List<Station>, station: Station) {
    val c = controller ?: return
    val id = Items.mediaId(category, station)
    if (now.mediaId == id) { togglePlay(); return }
    val items = list.map { Items.forStation(category, it) }
    val idx = list.indexOfFirst { it.id == station.id }.coerceAtLeast(0)
    c.setMediaItems(items, idx, 0)
    c.prepare()
    c.play()
  }

  fun togglePlay() {
    val c = controller ?: return
    if (c.playerError != null) { c.prepare(); c.play(); return }
    if (c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)) c.pause()
    else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
  }

  fun next() = controller?.seekToNextMediaItem()
  fun previous() = controller?.seekToPreviousMediaItem()

  fun toggleFavorite(s: Station) = Prefs.toggleFavorite(s)

  // ------------------------------------------------------------------ Musikerkennung

  fun streamAvailable(): Boolean = now.isPlaying

  /** Erkennt den laufenden Song. Läuft ein Sender, wird direkt sein Ton genutzt, sonst das Mikrofon. */
  fun recognize(micAllowed: Boolean) {
    if (recJob?.isActive == true) return
    recJob = viewModelScope.launch {
      val sample: Pair<ShortArray, Int>? = if (now.isPlaying) {
        // Die letzten Sekunden liegen schon im Speicher; ggf. kurz warten, bis genug da ist.
        var s: Pair<ShortArray, Int>? = null
        for (i in 0 until 20) {
          s = AudioTap.snapshot(Recognizer.SECONDS)
          if (s != null && s.first.size >= s.second * (Recognizer.SECONDS - 1)) break
          rec = RecState.Listening(i / 20f, fromStream = true)
          kotlinx.coroutines.delay(500)
        }
        s
      } else null

      val pcm = sample ?: run {
        if (!micAllowed) { rec = RecState.Message(com.keremik.radio.R.string.mic_denied); return@launch }
        rec = RecState.Listening(0f, fromStream = false)
        try {
          Recognizer.recordMic { p -> rec = RecState.Listening(p, fromStream = false) }
        } catch (e: Exception) {
          rec = RecState.Message(com.keremik.radio.R.string.rec_error, e.message); return@launch
        }
      }
      rec = RecState.Identifying
      rec = when (val r = Recognizer.identify(pcm.first, pcm.second)) {
        is RecognizeResult.Found -> {
          Prefs.addSong(r.song)
          if (Prefs.autoYoutube) openYouTube(r.song.query)
          RecState.Result(r.song)
        }
        RecognizeResult.NotFound -> RecState.Message(com.keremik.radio.R.string.rec_not_found)
        RecognizeResult.LimitReached -> RecState.Message(com.keremik.radio.R.string.rec_limit)
        is RecognizeResult.Failed -> RecState.Message(com.keremik.radio.R.string.rec_error, r.message)
      }
    }
  }

  fun cancelRecognize() { recJob?.cancel(); rec = RecState.Idle }

  fun openYouTube(query: String) {
    viewModelScope.launch { YouTube.open(getApplication(), query) }
  }

  /** Song aus den Sender-Infos übernehmen (in den Verlauf) und auf YouTube öffnen. */
  fun openNowPlayingOnYouTube() {
    val n = now
    val t = n.songTitle ?: return
    Prefs.addSong(Song(n.songArtist.orEmpty(), t, n.station?.name.orEmpty(), n.station?.favicon.orEmpty(), System.currentTimeMillis()))
    openYouTube(n.songQuery ?: t)
  }

  override fun onCleared() {
    controller?.release()
    super.onCleared()
  }
}
