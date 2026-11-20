package com.keremik.radio.playback

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.keremik.radio.R
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.Http
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Wiedergabe im Hintergrund. Über die MediaSession steuern Bluetooth (AVRCP: Play/Pause/Weiter/Zurück, Titelanzeige
 * im Autodisplay), Lenkradtasten, Kopfhörer, die Benachrichtigung, der Sperrbildschirm und Android Auto die App.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

  private lateinit var player: ExoPlayer
  private lateinit var session: MediaLibrarySession
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
  private val handler = Handler(Looper.getMainLooper())
  private var retries = 0

  private val favCommand = SessionCommand(CMD_TOGGLE_FAV, Bundle.EMPTY)

  override fun onCreate() {
    super.onCreate()

    val renderers = object : DefaultRenderersFactory(this) {
      override fun buildAudioSink(
        context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean,
      ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioTap)))
        .build()
    }

    val http = DefaultHttpDataSource.Factory()
      .setUserAgent(Http.USER_AGENT)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(12_000)
      .setReadTimeoutMs(20_000)

    player = ExoPlayer.Builder(this, renderers)
      .setMediaSourceFactory(
        DefaultMediaSourceFactory(this).setDataSourceFactory(http)
          .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
      )
      .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(20_000, 60_000, 2_000, 4_000).build())
      .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
        /* handleAudioFocus = */ true,
      )
      .setHandleAudioBecomingNoisy(true)
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .build()
    player.repeatMode = Player.REPEAT_MODE_ALL
    player.addListener(PlayerEvents())

    session = MediaLibrarySession.Builder(this, player, LibraryCallback())
      .setSessionActivity(
        android.app.PendingIntent.getActivity(
          this, 0, packageManager.getLaunchIntentForPackage(packageName),
          android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
      )
      .setMediaButtonPreferences(buttons(null))
      .build()

    setMediaNotificationProvider(
      DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
    )

    // Favoriten können sich in der App ändern – den Herz-Knopf im Auto/in der Benachrichtigung nachziehen.
    scope.launch { Prefs.favorites.collect { refreshButtons() } }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

  override fun onTaskRemoved(rootIntent: android.content.Intent?) {
    if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    handler.removeCallbacksAndMessages(null)
    session.release()
    player.release()
    super.onDestroy()
  }

  // ---------------------------------------------------------------- Knöpfe (Favorit) im Auto und in der Benachrichtigung

  private fun currentStation(): Station? =
    player.currentMediaItem?.mediaId?.let { Catalog.find(Items.stationId(it)) }

  private fun buttons(station: Station?): List<CommandButton> {
    val fav = station != null && Prefs.isFavorite(station.id)
    return listOf(
      CommandButton.Builder(if (fav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
        .setDisplayName(getString(if (fav) R.string.remove_fav else R.string.add_fav))
        .setSessionCommand(favCommand)
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()
    )
  }

  private fun refreshButtons() {
    if (::session.isInitialized) session.setMediaButtonPreferences(buttons(currentStation()))
  }

  // ---------------------------------------------------------------- Player-Ereignisse

  private inner class PlayerEvents : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      retries = 0
      mediaItem ?: return
      Prefs.lastMediaId = mediaItem.mediaId
      // Alten Songtitel zurücksetzen, bis der neue Sender seinen eigenen schickt.
      val s = Catalog.find(Items.stationId(mediaItem.mediaId))
      if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && s != null &&
        mediaItem.mediaMetadata.extras?.containsKey(Items.EXTRA_ICY) == true
      ) {
        updateCurrentMetadata(Items.baseMetadata(s))
      }
      refreshButtons()
    }

    override fun onMetadata(metadata: Metadata) {
      for (i in 0 until metadata.length()) {
        val e = metadata[i]
        if (e is IcyInfo) {
          val item = player.currentMediaItem ?: return
          val s = Catalog.find(Items.stationId(item.mediaId)) ?: return
          val t = e.title?.trim().orEmpty()
          val md = if (Items.isUsefulStreamTitle(t, s.name)) Items.withSong(Items.baseMetadata(s), s.name, t, turkish = s.country == "TR")
          else Items.baseMetadata(s)
          if (md.title != item.mediaMetadata.title || md.artist != item.mediaMetadata.artist) updateCurrentMetadata(md)
        }
      }
    }

    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_READY) retries = 0
    }

    override fun onPlayerError(error: PlaybackException) {
      // Kurze Netzaussetzer (Tunnel, Funkloch): bis zu 3-mal neu verbinden.
      if (retries < 3) {
        retries++
        handler.postDelayed({
          if (player.playerError != null) { player.prepare(); player.play() }
        }, 2_500L * retries)
      }
    }
  }

  private fun updateCurrentMetadata(md: MediaMetadata) {
    val idx = player.currentMediaItemIndex
    val item = player.currentMediaItem ?: return
    player.replaceMediaItem(idx, item.buildUpon().setMediaMetadata(md).build())
  }

  // ---------------------------------------------------------------- Bibliothek für Android Auto und Mediensteuerungen

  private fun categoryTitle(cat: String) = getString(
    when (cat) { Catalog.FAV -> R.string.tab_fav; Catalog.DE -> R.string.tab_de; else -> R.string.tab_tr }
  )

  private suspend fun itemsOf(cat: String): List<MediaItem> = Catalog.stations(cat).map { Items.forStation(cat, it) }

  /** Löst eine mediaId (oder eine Suchanfrage) zu einem abspielbaren Eintrag mit Adresse auf. */
  private suspend fun resolve(item: MediaItem): MediaItem? {
    val search = item.requestMetadata.searchQuery
    if (!search.isNullOrBlank()) {
      return Catalog.search(search).firstOrNull()?.let { Items.forStation(it.country.lowercase(), it) }
    }
    val cat = Items.category(item.mediaId).ifEmpty { Catalog.FAV }
    val id = Items.stationId(item.mediaId)
    val s = Catalog.find(id) ?: Catalog.stations(Catalog.DE).firstOrNull { it.id == id }
      ?: Catalog.stations(Catalog.TR).firstOrNull { it.id == id }
    return s?.let { Items.forStation(cat, it) }
  }

  private inner class LibraryCallback : MediaLibrarySession.Callback {

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
      val base = MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailableSessionCommands(base.availableSessionCommands.buildUpon().add(favCommand).build())
        .setAvailablePlayerCommands(base.availablePlayerCommands)
        .setMediaButtonPreferences(buttons(currentStation()))
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle,
    ): ListenableFuture<SessionResult> {
      if (customCommand.customAction == CMD_TOGGLE_FAV) {
        currentStation()?.let { Prefs.toggleFavorite(it) }
        refreshButtons()
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
      }
      return super.onCustomCommand(session, controller, customCommand, args)
    }

    override fun onGetLibraryRoot(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
      Futures.immediateFuture(LibraryResult.ofItem(Items.folder(Items.ROOT, getString(R.string.app_name),
        MediaMetadata.MEDIA_TYPE_FOLDER_MIXED), params))

    override fun onGetChildren(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
      page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
      val all: List<MediaItem> = when (parentId) {
        Items.ROOT -> listOf(Catalog.FAV, Catalog.DE, Catalog.TR).map { Items.folder(it, categoryTitle(it)) }
        Catalog.FAV, Catalog.DE, Catalog.TR -> itemsOf(parentId)
        else -> return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
      }
      val from = (page * pageSize).coerceAtMost(all.size)
      val to = (from + pageSize).coerceAtMost(all.size)
      LibraryResult.ofItemList(all.subList(from, to), params)
    }

    override fun onGetItem(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
      if (mediaId in listOf(Catalog.FAV, Catalog.DE, Catalog.TR)) return@future LibraryResult.ofItem(Items.folder(mediaId, categoryTitle(mediaId)), null)
      resolve(MediaItem.Builder().setMediaId(mediaId).build())?.let { LibraryResult.ofItem(it, null) }
        ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
    }

    override fun onSearch(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> = scope.future {
      val n = Catalog.search(query).size
      session.notifySearchResultChanged(browser, query, n, params)
      LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
      session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
      page: Int, pageSize: Int, params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
      val all = Catalog.search(query).map { Items.forStation(it.country.lowercase(), it) }
      val from = (page * pageSize).coerceAtMost(all.size)
      LibraryResult.ofItemList(all.subList(from, (from + pageSize).coerceAtMost(all.size)), params)
    }

    override fun onAddMediaItems(
      mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> = scope.future {
      mediaItems.mapNotNull { resolve(it) }.toMutableList()
    }

    /**
     * Wird ein einzelner Sender gewählt (z. B. in Android Auto oder per Sprachbefehl), kommt die ganze Liste dazu,
     * damit Weiter/Zurück am Lenkrad durch die Sender dieser Liste schalten.
     */
    override fun onSetMediaItems(
      mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
      startIndex: Int, startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
      if (mediaItems.size == 1) {
        val one = resolve(mediaItems[0])
          ?: return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
        val cat = Items.category(one.mediaId)
        val list = itemsOf(cat)
        val idx = list.indexOfFirst { it.mediaId == one.mediaId }
        if (idx >= 0) MediaSession.MediaItemsWithStartPosition(list, idx, C.TIME_UNSET)
        else MediaSession.MediaItemsWithStartPosition(listOf(one), 0, C.TIME_UNSET)
      } else {
        val resolved = mediaItems.mapNotNull { resolve(it) }
        val wanted = mediaItems.getOrNull(startIndex)?.mediaId
        val idx = resolved.indexOfFirst { it.mediaId == wanted }.coerceAtLeast(0)
        MediaSession.MediaItemsWithStartPosition(resolved, idx, C.TIME_UNSET)
      }
    }

    /** Play-Taste im Auto, obwohl die App nicht läuft: mit dem zuletzt gehörten Sender weitermachen. */
    override fun onPlaybackResumption(
      mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
      val last = Prefs.lastMediaId
      var cat = Items.category(last).ifEmpty { Catalog.FAV }
      var list = itemsOf(cat)
      if (list.isEmpty()) { cat = Catalog.DE; list = itemsOf(cat) }
      val idx = list.indexOfFirst { it.mediaId == last }.coerceAtLeast(0)
      MediaSession.MediaItemsWithStartPosition(list, idx, C.TIME_UNSET)
    }
  }

  companion object {
    const val CMD_TOGGLE_FAV = "toggle_favorite"
  }
}
