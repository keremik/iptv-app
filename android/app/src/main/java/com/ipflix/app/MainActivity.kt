package com.ipflix.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.http.HttpResponseCache
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// Die komplette Oberflaeche laeuft in einer WebView (dieselbe, getestete App).
// Nur die Wiedergabe uebernimmt ein nativer Player (ExoPlayer), damit auch
// mkv, Live-TS und HLS fluessig laufen. Der Stream geht direkt an den Anbieter.
@UnstableApi
class MainActivity : AppCompatActivity() {

  private lateinit var root: FrameLayout
  private lateinit var webView: WebView
  private lateinit var overlay: FrameLayout
  private lateinit var playerView: PlayerView
  private lateinit var titleView: TextView
  private lateinit var subtitleView: TextView
  private lateinit var infoBar: View
  private var player: ExoPlayer? = null
  private lateinit var assetLoader: WebViewAssetLoader
  private val main = Handler(Looper.getMainLooper())

  private var altUrl = ""
  private var triedAlt = false
  private var isLive = false
  private var playToken = 0

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(saved: Bundle?) {
    super.onCreate(saved)
    // Zwischenspeicher fuer Bilder (Poster/Logos), damit sie nicht jedes Mal neu geladen werden
    try { HttpResponseCache.install(File(cacheDir, "http"), 60L * 1024 * 1024) } catch (e: Exception) {}
    assetLoader = WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()
    root = FrameLayout(this)

    webView = WebView(this)
    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      mediaPlaybackRequiresUserGesture = false
      cacheMode = WebSettings.LOAD_DEFAULT
      allowFileAccess = true
      allowContentAccess = true
      mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
    webView.webViewClient = object : WebViewClient() {
      // Die Oberflaeche bleibt in der App; echte Links (z. B. App Store) oeffnen extern,
      // damit ein versehentlicher Klick die App nicht ersetzt.
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (request.url.host == WebViewAssetLoader.DEFAULT_DOMAIN) return false
        val scheme = request.url.scheme ?: ""
        if (scheme == "http" || scheme == "https") {
          try { startActivity(Intent(Intent.ACTION_VIEW, request.url)) } catch (e: Exception) {}
          return true
        }
        return false
      }

      // Die Oberflaeche liegt unter https://appassets.androidplatform.net/assets/web/ (eigene, sichere Adresse).
      // /proxy?u=... holt Daten und Bilder direkt beim Anbieter des Nutzers, ohne fremden Server.
      override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == WebViewAssetLoader.DEFAULT_DOMAIN && url.path == "/proxy") {
          val target = url.getQueryParameter("u") ?: return textResponse(400, "Adresse fehlt")
          return proxyFetch(target)
        }
        return assetLoader.shouldInterceptRequest(url)
      }
    }
    webView.webChromeClient = WebChromeClient()
    webView.setBackgroundColor(Color.BLACK)
    webView.overScrollMode = View.OVER_SCROLL_NEVER   // kein Überzieh-Glühen, spart Zeichenaufwand
    webView.addJavascriptInterface(Bridge(), "NativePlayer")
    root.addView(webView, matchParent())

    player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory()).build().also {
      it.addListener(playerListener)
      // Sauberer Ton-Fokus: bei Anrufen/anderem Ton automatisch pausieren.
      it.setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
          .setUsage(C.USAGE_MEDIA).build(),
        true)
      it.setHandleAudioBecomingNoisy(true)
    }
    playerView = PlayerView(this).apply {
      player = this@MainActivity.player
      useController = true
      controllerShowTimeoutMs = 3500
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
      setBackgroundColor(Color.BLACK)
      isFocusable = true
      isFocusableInTouchMode = true
    }
    overlay = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(Color.BLACK)
    }
    overlay.addView(playerView, matchParent())

    val bar = LinearLayout(this).apply {
      visibility = View.GONE
      orientation = LinearLayout.VERTICAL
      setPadding(dp(28), dp(18), dp(28), dp(18))
      setBackgroundColor(0xB3000000.toInt())
    }
    titleView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f }
    subtitleView = TextView(this).apply { setTextColor(0xFFB3B3B3.toInt()); textSize = 14f; visibility = View.GONE }
    bar.addView(titleView)
    bar.addView(subtitleView)
    overlay.addView(bar, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
    root.addView(overlay, matchParent())
    infoBar = bar
    // Titel/Programm nur zusammen mit der Steuerung zeigen (und kurz nach Start oder Senderwechsel)
    playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
      if (v == View.VISIBLE) showInfo(0) else showInfo(1500)
    })

    setContentView(root)
    // Fernbedienung soll sofort in der Oberflaeche landen (sonst reagiert der erste Tastendruck nicht)
    webView.requestFocus()
    if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
    webView.loadUrl("https://" + WebViewAssetLoader.DEFAULT_DOMAIN + "/assets/web/index.html")
  }

  // Holt eine Adresse beim Anbieter (folgt Weiterleitungen, auch auf IP-Adressen) und gibt sie an die Oberflaeche.
  private fun proxyFetch(target: String): WebResourceResponse {
    return try {
      var u = URL(target)
      if (u.protocol != "http" && u.protocol != "https") return textResponse(400, "Nur http/https")
      var conn: HttpURLConnection
      var hops = 0
      while (true) {
        conn = u.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "*/*")
        val code = conn.responseCode
        val loc = conn.getHeaderField("Location")
        if (code in 300..399 && loc != null && hops < 5) {
          conn.disconnect(); u = URL(u, loc); hops++; continue
        }
        break
      }
      val code = conn.responseCode
      if (code in 300..399) return textResponse(502, "Zu viele Weiterleitungen")
      val ct = conn.contentType ?: "application/octet-stream"
      val mime = ct.substringBefore(";").trim().ifEmpty { "application/octet-stream" }
      val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)?.trim() ?: "utf-8"
      val stream = if (code >= 400) (conn.errorStream ?: ByteArrayInputStream(ByteArray(0))) else conn.inputStream
      val reason = conn.responseMessage?.takeIf { it.isNotBlank() } ?: (if (code < 400) "OK" else "Error")
      WebResourceResponse(mime, charset, code, reason, mapOf("Access-Control-Allow-Origin" to "*"), stream)
    } catch (e: Exception) {
      textResponse(502, "Anbieter nicht erreichbar: " + (e.message ?: e.javaClass.simpleName))
    }
  }

  private fun textResponse(code: Int, msg: String) = WebResourceResponse(
    "text/plain", "utf-8", code, if (code < 400) "OK" else "Error",
    mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(msg.toByteArray()))

  private fun mediaSourceFactory(): DefaultMediaSourceFactory {
    val http = DefaultHttpDataSource.Factory()
      .setUserAgent(UA)
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15000)
      .setReadTimeoutMs(15000)
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
  }

  private val playerListener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
      val p = player
      // Live zu lange pausiert ("hinter dem Live-Fenster"): einfach wieder am aktuellen Live-Punkt einsteigen
      if (p != null && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        p.seekToDefaultPosition(); p.prepare(); p.playWhenReady = true
        return
      }
      if (altUrl.isNotEmpty() && !triedAlt) {
        triedAlt = true
        player?.let { p ->
          p.setMediaItem(MediaItem.fromUri(altUrl)); p.prepare(); p.playWhenReady = true
        }
      } else {
        hidePlayer()
        runJs("window.__player&&__player.error&&__player.error(" + jsStr(friendlyError(error)) + ")")
      }
    }

    // Erstes Bild laeuft: erst ab jetzt zaehlt die Sehzeit fuer "Deine Sender"
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (isPlaying) runJs("window.__player&&__player.playing&&__player.playing()")
    }

    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_READY) startTimer()
      if (state == Player.STATE_ENDED) {
        if (isLive) {
          // Live-Stream vom Anbieter beendet: zurueck zur Liste mit Hinweis.
          hidePlayer()
          runJs("window.__player&&__player.error&&__player.error(" + jsStr("Live-Stream beendet") + ")")
        } else {
          val tok = playToken
          runJs("window.__player&&__player.ended&&__player.ended()")
          // Spielt die Oberflaeche nichts Neues an (z. B. Film zu Ende), Player schliessen.
          main.postDelayed({
            if (playToken == tok && player?.playbackState == Player.STATE_ENDED) {
              hidePlayer(); runJs("window.__player&&__player.back&&__player.back()")
            }
          }, 1500)
        }
      }
    }
  }

  // Verstaendliche Fehlermeldung statt technischem Code
  private fun friendlyError(e: PlaybackException): String {
    var c: Throwable? = e.cause
    while (c != null) {
      if (c is HttpDataSource.InvalidResponseCodeException) {
        val code = c.responseCode
        return when (code) {
          401, 403 -> "Der Anbieter lehnt den Stream ab (HTTP $code). Oft läuft schon ein Stream auf einem anderen Gerät, oder das Abo ist abgelaufen."
          404 -> "Diesen Stream gibt es beim Anbieter nicht mehr (HTTP 404)."
          else -> "Der Anbieter meldet einen Fehler (HTTP $code)."
        }
      }
      c = c.cause
    }
    return when (e.errorCode) {
      PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
      PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Keine Verbindung zum Anbieter. Internet prüfen und erneut versuchen."
      PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
      PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
      PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
      PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
      PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
      PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Dieses Format kann das Gerät nicht abspielen. Eine andere Variante (z. B. HD statt 4K) probieren."
      else -> "Wiedergabe nicht möglich. Bitte erneut versuchen."
    }
  }

  // Aktuelle Position an die Oberflaeche melden (fuer "Weiterschauen")
  private fun reportTime() {
    val p = player ?: return
    if (isLive || overlay.visibility != View.VISIBLE) return
    val pos = p.currentPosition / 1000.0
    val dur = if (p.duration > 0) p.duration / 1000.0 else 0.0
    if (pos > 0) runJs("window.__player&&__player.time&&__player.time($pos,$dur)")
  }

  // Bevorzugte Tonspur/Untertitel nach den gewaehlten Sprachen (z. B. Deutsch vor Tuerkisch)
  private val ISO = mapOf("de" to listOf("de"), "tr" to listOf("tr"), "en" to listOf("en"), "fr" to listOf("fr"),
    "ar" to listOf("ar"), "ku" to listOf("ku"), "es" to listOf("es"), "it" to listOf("it"), "pl" to listOf("pl"),
    "nl" to listOf("nl"), "pt" to listOf("pt"), "ru" to listOf("ru"), "bk" to listOf("sr", "hr", "bs"), "al" to listOf("sq"))
  private fun applyLangs(csv: String) {
    val p = player ?: return
    val codes = csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.flatMap { ISO[it] ?: listOf(it) }
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setPreferredAudioLanguages(*codes.toTypedArray())
      .setPreferredTextLanguages(*codes.toTypedArray())
      .build()
  }

  inner class Bridge {
    @JavascriptInterface fun langs(csv: String) { main.post { applyLangs(csv) } }
    @JavascriptInterface fun isApp(): Boolean = true
    // Schwaches Geraet? Dann schaltet die Oberflaeche teure Effekte ab und laedt weniger auf einmal.
    @JavascriptInterface fun lite(): Boolean {
      return try {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.isLowRamDevice) return true
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem in 1..1_600_000_000L
      } catch (e: Exception) { false }
    }
    @JavascriptInterface fun play(url: String, live: Boolean, title: String, startSec: Int, alt: String) {
      main.post { showPlayer(url, live, title, startSec, alt) }
    }
    @JavascriptInterface fun stop() { main.post { hidePlayer() } }
    @JavascriptInterface fun subtitle(text: String) {
      main.post {
        subtitleView.text = text
        subtitleView.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (overlay.visibility == View.VISIBLE && text.isNotEmpty() && !playerView.isControllerFullyVisible) showInfo(6000)
      }
    }
  }

  private fun showPlayer(url: String, live: Boolean, title: String, startSec: Int, alt: String) {
    val p = player ?: return
    playToken++
    altUrl = alt; triedAlt = false; isLive = live
    titleView.text = title
    subtitleView.text = ""; subtitleView.visibility = View.GONE
    p.setMediaItem(MediaItem.fromUri(url))
    p.prepare()
    if (!live && startSec > 0) p.seekTo(startSec * 1000L)
    p.playWhenReady = true
    overlay.visibility = View.VISIBLE
    overlay.bringToFront()
    webView.isFocusable = false
    webView.isFocusableInTouchMode = false
    playerView.requestFocus()
    showInfo(6000)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    startTimer()
  }

  private val hideInfo = Runnable { if (!playerView.isControllerFullyVisible) infoBar.visibility = View.GONE }
  // ms = 0: sichtbar lassen (Steuerung offen); sonst nach ms ausblenden
  private fun showInfo(ms: Long) {
    main.removeCallbacks(hideInfo)
    infoBar.visibility = View.VISIBLE
    if (ms > 0) main.postDelayed(hideInfo, ms)
  }

  private fun hidePlayer() {
    playToken++
    val wasShown = overlay.visibility == View.VISIBLE
    player?.let { it.playWhenReady = false; it.stop(); it.clearMediaItems() }
    overlay.visibility = View.GONE
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    // Fokus nur zurueckgeben, wenn der Player wirklich offen war (sonst springt die Auswahl unnoetig)
    if (wasShown || !webView.isFocusable) {
      webView.isFocusable = true
      webView.isFocusableInTouchMode = true
      webView.requestFocus()
    }
  }

  private val ticker = object : Runnable {
    override fun run() {
      if (overlay.visibility != View.VISIBLE) return   // gestoppt bis zum naechsten showPlayer
      val p = player
      if (p != null && p.isPlaying && !isLive) {
        val pos = p.currentPosition / 1000.0
        val dur = if (p.duration > 0) p.duration / 1000.0 else 0.0
        runJs("window.__player&&__player.time&&__player.time($pos,$dur)")
      }
      main.postDelayed(this, 5000)
    }
  }
  private fun startTimer() { main.removeCallbacks(ticker); main.postDelayed(ticker, 5000) }

  private fun runJs(js: String) { webView.post { webView.evaluateJavascript(js, null) } }
  private fun jsStr(s: String?): String =
    "\"" + (s ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val k = event.keyCode
    val down = event.action == KeyEvent.ACTION_DOWN
    if (overlay.visibility == View.VISIBLE) {
      // Lautstaerke & Co. bleiben beim System
      if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
        return super.dispatchKeyEvent(event)
      }
      if (k == KeyEvent.KEYCODE_BACK) {
        if (down && event.repeatCount == 0) {
          reportTime()
          hidePlayer(); runJs("window.__player&&__player.back&&__player.back()")
        }
        return true
      }
      if (down && event.repeatCount == 0) {
        when (k) {
          KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
            runJs("window.__player&&__player.switch&&__player.switch(1)"); return true
          }
          KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true
          }
          KeyEvent.KEYCODE_DPAD_UP -> if (isLive && !playerView.isControllerFullyVisible) {
            runJs("window.__player&&__player.switch&&__player.switch(1)"); return true
          }
          KeyEvent.KEYCODE_DPAD_DOWN -> if (isLive && !playerView.isControllerFullyVisible) {
            runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true
          }
        }
      }
      // alles andere (OK, links/rechts zum Spulen, Play/Pause) bekommt nur der Player
      playerView.dispatchKeyEvent(event)
      return true
    }
    if (k == KeyEvent.KEYCODE_BACK) {
      if (down && event.repeatCount == 0) {
        webView.evaluateJavascript("(window.__appBack&&window.__appBack())?'1':'0'") { r ->
          // Auf der Startseite die App nur in den Hintergrund legen (Zustand bleibt erhalten).
          if (r == null || r.trim('"') != "1") moveTaskToBack(true)
        }
      }
      return true
    }
    // OK auf einem Eingabefeld oeffnet die Bildschirmtastatur: Oberflaeche schiebt das Feld nach oben
    if (!down && (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
      val handled = super.dispatchKeyEvent(event)
      runJs("window.__kbd&&__kbd()")
      return handled
    }
    return super.dispatchKeyEvent(event)
  }

  // App verlassen (z. B. Home-Taste): Sehzeit und Fortschritt sichern; beim Zurueckkommen neu beginnen
  override fun onPause() {
    reportTime()
    runJs("window.__player&&__player.hidden&&__player.hidden()")
    super.onPause(); player?.pause()
  }
  override fun onResume() {
    super.onResume()
    runJs("window.__player&&__player.shown&&__player.shown()")
    val p = player
    if (p != null && isLive && overlay.visibility == View.VISIBLE) { p.seekToDefaultPosition(); p.play() }
  }

  override fun onDestroy() {
    main.removeCallbacksAndMessages(null)
    player?.release(); player = null
    webView.destroy()
    super.onDestroy()
  }

  companion object { const val UA = "IPTVSmartersPro" }

  private fun matchParent() = FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
