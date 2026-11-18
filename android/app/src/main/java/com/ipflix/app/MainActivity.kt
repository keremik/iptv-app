package com.ipflix.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.http.HttpResponseCache
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
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

  private var isLive = false
  // Senderliste im Player (Live): Namen, aktuelle Position, vorheriger Sender
  private lateinit var chanPanel: LinearLayout
  private lateinit var chanList: android.widget.ListView
  private var chanItems = listOf<String>()
  private var chanHasPrev = false
  private var chanIdx = 0
  private var digits = ""
  private var swallowOkUp = false
  // eigenes Menü für Untertitel/Tonspur (das Menü des Players blieb hängen, wenn sich die Leiste ausblendete)
  private lateinit var trackPanel: LinearLayout
  private lateinit var trackTitle: TextView
  private lateinit var trackList: android.widget.ListView
  private var trackActions = listOf<() -> Unit>()
  // nächste Folge: Hinweis mit Countdown in den letzten Sekunden
  private var nextLabel = ""
  private lateinit var nextView: TextView
  // Ausfallsicherheit: Adressen dieses Senders (HLS, TS), Wiederholversuche, Hänger-Erkennung
  private var urls = listOf<String>()
  private var urlIdx = 0
  private var attempts = 0
  private var authRetry = false
  private var stallSince = 0L
  private var lastBuffered = 0L
  private var lastProgress = 0L
  private var bufStart = 0L
  @Volatile private var lastBytesAt = 0L
  @Volatile private var bytesTotal = 0L
  // Messung der tatsächlichen Stream-Qualität (Live): Auflösung, Bildrate, Datenrate, Hänger
  private var qUrl = ""; private var qT0 = 0L; private var qB0 = 0L; private var qF0 = -1L; private var qD0 = 0
  private var qStalls = 0; private var qReady = false; private var qToken = -1
  private lateinit var qualityView: TextView
  private var loadStartAt = 0L
  private var retryTask: Runnable? = null
  private lateinit var statusView: TextView
  // Bildwiederholrate an die Sendung anpassen (50 Hz für Fernsehen, 24 Hz für Filme)
  private var afrEnabled = true
  @Volatile private var syncSink: SyncSink? = null
  private val syncMeter = SyncMeter { syncSink }
  private var avOffsetMs = 0
  private var syncTest = false
  // ---------- Eingebauter Browser (z. B. YouTube-Suche nach einem Trailer) ----------
  private var browser: WebView? = null
  private fun openBrowser(url: String) {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return
    closeBrowser()
    val b = WebView(this)
    b.settings.apply {
      javaScriptEnabled = true; domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      setSupportMultipleWindows(false)
    }
    b.setBackgroundColor(Color.BLACK)
    b.webChromeClient = WebChromeClient()
    b.webViewClient = object : WebViewClient() {
      // nur Webseiten im Browser; App-Links (intent:, vnd.youtube:) nicht öffnen, damit IPFLIX vorne bleibt
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val sch = request.url.scheme ?: ""
        return !(sch == "https" || sch == "http")
      }
      override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        main.post { if (browser === view) { browser = null; try { root.removeView(view); view.destroy() } catch (e: Exception) {} ; webView.requestFocus() } }
        return true
      }
    }
    root.addView(b, matchParent())
    b.bringToFront()
    browser = b
    b.isFocusable = true; b.isFocusableInTouchMode = true
    b.requestFocus()
    b.loadUrl(url)
  }
  private fun closeBrowser() {
    val b = browser ?: return
    browser = null
    try { b.loadUrl("about:blank"); root.removeView(b); b.destroy() } catch (e: Exception) {}
    webView.requestFocus()
    runJs("window.__player&&__player.browserClosed&&__player.browserClosed()")
  }
  // ---------- Senderstatus (Ampel) ----------
  private val probeExec = java.util.concurrent.Executors.newSingleThreadExecutor()
  @Volatile private var probeConn: HttpURLConnection? = null
  @Volatile private var playerOpen = false
  private fun doProbe(target: String): String {
    val t0 = SystemClock.uptimeMillis()
    var ttfb = -1L; var bytes = 0L; var maxGap = 0L; var code = 0; var first = 0L; var last = 0L
    var conn: HttpURLConnection? = null
    try {
      if (playerOpen) return "{\"skip\":true}"
      conn = (URL(target).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5000; readTimeout = 3000; instanceFollowRedirects = true
        setRequestProperty("User-Agent", UA)
      }
      probeConn = conn
      code = conn.responseCode
      if (code in 200..299) {
        val ins = conn.inputStream
        val buf = ByteArray(16 * 1024)
        while (!playerOpen) {
          val n = ins.read(buf)
          if (n < 0) break
          val now = SystemClock.uptimeMillis()
          if (ttfb < 0) { ttfb = now - t0; first = now } else maxGap = maxOf(maxGap, now - last)
          last = now; bytes += n
          if (now - first > 4000 || bytes > 8_000_000) break
        }
      }
    } catch (e: Exception) {
      if (last > 0) maxGap = maxOf(maxGap, SystemClock.uptimeMillis() - last)
    } finally {
      try { conn?.disconnect() } catch (e: Exception) {}
      probeConn = null
    }
    if (playerOpen) return "{\"skip\":true}"
    val secs = if (last > first) (last - first) / 1000.0 else 0.0
    val kbps = if (secs > 0.5) (bytes * 8 / 1000.0 / secs).toInt() else 0
    return "{\"code\":$code,\"ttfb\":$ttfb,\"bytes\":$bytes,\"gap\":$maxGap,\"kbps\":$kbps}"
  }
  private fun setAvOffset(ms: Int) { avOffsetMs = ms.coerceIn(-500, 500); syncSink?.offsetUs = avOffsetMs * 1000L }
  private fun offsetText(ms: Int) = when { ms > 0 -> "Ton $ms ms später"; ms < 0 -> "Ton ${-ms} ms früher"; else -> "Ton unverändert (0 ms)" }
  private fun startSyncTest() {
    showPlayer("asset:///synctest.ts", false, "Ton-Test", 0, "")
    syncTest = true
    syncMeter.active = true
    player?.repeatMode = Player.REPEAT_MODE_ONE
    showSyncInfo()
    main.postDelayed(syncTick, 3000)
  }
  private val syncTick: Runnable = object : Runnable {
    override fun run() { if (!syncTest) return; showSyncInfo(); main.postDelayed(this, 3000) }
  }
  private fun showSyncInfo() {
    qualityView.visibility = View.VISIBLE
    qualityView.text = "◀ ▶ 10 ms · ▲ ▼ 50 ms · OK = 0 · Zurück = speichern\n" + (syncMeter.last.ifEmpty { "Messung läuft …" })
    titleView.text = "Ton-Test: " + offsetText(avOffsetMs)
    showInfo(0)
  }
  private var origModeId = 0
  private var modeChanged = false
  private var fpsToken = -1
  // Doppelklick links/rechts: 10 Sekunden springen (wie bei Netflix)
  private lateinit var skipView: TextView
  private var skipDir = 0
  private var lastTap = 0L
  private var skipActive = false
  private var skipSum = 0
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

      // Beendet Android die Oberfläche (z. B. Speicher knapp), nicht die ganze App abstürzen lassen,
      // sondern die Oberfläche neu aufbauen.
      override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
        android.util.Log.w("IPFLIX", "Oberfläche beendet (crash=${detail.didCrash()}), baue neu auf")
        try { root.removeView(view); view.destroy() } catch (e: Exception) {}
        // stürzt sie sofort wieder ab, nicht endlos neu starten
        val now = SystemClock.elapsedRealtime()
        val again = now - lastUiGone < 20000
        lastUiGone = now
        main.post { if (again) finish() else recreate() }
        return true
      }
    }
    webView.webChromeClient = WebChromeClient()
    webView.setBackgroundColor(Color.BLACK)
    webView.overScrollMode = View.OVER_SCROLL_NEVER   // kein Überzieh-Glühen, spart Zeichenaufwand
    webView.addJavascriptInterface(Bridge(), "NativePlayer")
    root.addView(webView, matchParent())

    // Puffer für flüssige Wiedergabe: schneller Start (1,5 s), danach bis 50 s vorladen; nach einem Hänger 3 s sammeln
    // Live braucht einen Vorlauf-Polster wie bei TiviMate/VLC: Start nach 2,5 s, nach einem Aussetzer 5 s sammeln.
    // Filme starten weiter nach 1 s.
    val loadControl = object : DefaultLoadControl(
      androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
      20_000, 50_000, 1_000, 2_500, 96 * 1024 * 1024, false, 0, false) {
      override fun shouldStartPlayback(timeline: androidx.media3.common.Timeline,
                                       id: androidx.media3.exoplayer.source.MediaSource.MediaPeriodId,
                                       bufferedDurationUs: Long, playbackSpeed: Float, rebuffering: Boolean,
                                       targetLiveOffsetUs: Long): Boolean {
        if (!isLive) return super.shouldStartPlayback(timeline, id, bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)
        return bufferedDurationUs >= (if (rebuffering) LIVE_REBUFFER_US else LIVE_START_US)
      }
    }
    // Beste Qualität von Anfang an: hohe Startschätzung der Bandbreite statt mit der kleinsten Stufe zu beginnen
    val meter = DefaultBandwidthMeter.Builder(this).setInitialBitrateEstimate(25_000_000L).build()
    // Bei Streams mit mehreren Stufen (HLS): schneller hoch auf die beste Stufe (5 s statt 10 s),
    // mehr der gemessenen Bandbreite nutzen (85 % statt 70 %), nur bei echtem Engpass herunter.
    val adaptive = androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(5_000, 25_000, 25_000, 0.85f)
    val selector = DefaultTrackSelector(this, adaptive).apply {
      setParameters(buildUponParameters()
        .setViewportSizeToPhysicalDisplaySize(this@MainActivity, true)
        .setExceedRendererCapabilitiesIfNecessary(true))
    }
    // Hardware-Decoder zuerst, bei Problemen automatisch auf einen anderen Decoder ausweichen
    val renderers = object : DefaultRenderersFactory(this) {
      // Ton-Uhr mit einstellbarem Versatz (Einstellungen → Ton-Versatz)
      override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? =
        super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)?.let { SyncSink(it).also { s -> s.offsetUs = avOffsetMs * 1000L; syncSink = s } }
    }.setEnableDecoderFallback(true)
    player = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(mediaSourceFactory())
      .setLoadControl(loadControl).setBandwidthMeter(meter).setTrackSelector(selector)
      .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build().also {
      it.addListener(playerListener)
      it.setVideoFrameMetadataListener(syncMeter)
      // Sauberer Ton-Fokus: bei Anrufen/anderem Ton automatisch pausieren.
      it.setAudioAttributes(
        AudioAttributes.Builder()
          .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
          .setUsage(C.USAGE_MEDIA).build(),
        true)
      it.setHandleAudioBecomingNoisy(true)
      // Untertitel nie automatisch (auch nicht, wenn die Datei sie als "Standard" markiert); erzwungene bleiben
      it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
        .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
      // WLAN-Energiesparen während der Wiedergabe aus (sonst Datenlücken = Hänger), Gerät bleibt wach
      it.setWakeMode(C.WAKE_MODE_NETWORK)
    }
    playerView = PlayerView(this).apply {
      player = this@MainActivity.player
      useController = true
      controllerShowTimeoutMs = 5000
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
      setShowSubtitleButton(true)          // Untertitel-Knopf (CC): an/aus und Sprache wählen
      setBackgroundColor(Color.BLACK)
      isFocusable = true
      isFocusableInTouchMode = true
      // requestFocus() soll den Player selbst auswählen, nicht irgendeinen Knopf (sonst landete die Auswahl auf dem Zahnrad)
      descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
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
    qualityView = TextView(this).apply { setTextColor(0xFF8C8C8C.toInt()); textSize = 13f; visibility = View.GONE }
    bar.addView(titleView)
    bar.addView(subtitleView)
    bar.addView(qualityView)
    overlay.addView(bar, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
    skipView = TextView(this).apply {
      setTextColor(Color.WHITE); textSize = 24f
      setPadding(dp(26), dp(14), dp(26), dp(14))
      background = GradientDrawable().apply { cornerRadius = dp(40).toFloat(); setColor(0xA6000000.toInt()) }
      visibility = View.GONE
    }
    overlay.addView(skipView, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START))
    statusView = TextView(this).apply {
      setTextColor(Color.WHITE); textSize = 18f
      setPadding(dp(24), dp(12), dp(24), dp(12))
      background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0xCC000000.toInt()) }
      visibility = View.GONE
    }
    overlay.addView(statusView, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
      bottomMargin = dp(90)
    })
    watchNetwork()
    // Touch-Geräte: Doppeltippen links/rechts
    val taps = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
      override fun onDoubleTap(e: MotionEvent): Boolean {
        if (isLive) return false
        skip(if (e.x < playerView.width / 2f) -1 else 1); return true
      }
    })
    playerView.setOnTouchListener { _, e -> taps.onTouchEvent(e); false }
    // Senderliste links (OK im Live-TV), wie bei TiviMate
    chanPanel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xE6101010.toInt())
      setPadding(dp(12), dp(20), dp(12), dp(20))
      visibility = View.GONE
    }
    chanPanel.addView(TextView(this).apply { text = "Sender"; setTextColor(Color.WHITE); textSize = 20f; setPadding(dp(14), 0, 0, dp(10)) })
    chanList = android.widget.ListView(this).apply {
      divider = null
      setSelector(GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(0x40FFFFFF) })
      isFocusable = true
      setOnItemClickListener { _, _, pos, _ -> pickChannel(pos) }
      setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(a: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { bumpChanTimer() }
        override fun onNothingSelected(a: android.widget.AdapterView<*>?) {}
      })
    }
    chanPanel.addView(chanList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    overlay.addView(chanPanel, FrameLayout.LayoutParams(dp(420), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
    trackPanel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xE6101010.toInt())
      setPadding(dp(12), dp(20), dp(12), dp(20))
      visibility = View.GONE
    }
    trackTitle = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; setPadding(dp(14), 0, 0, dp(10)) }
    trackPanel.addView(trackTitle)
    trackList = android.widget.ListView(this).apply {
      divider = null
      setSelector(GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(0x40FFFFFF) })
      isFocusable = true
      setOnItemClickListener { _, _, pos, _ -> val a = trackActions.getOrNull(pos); hideTracks(); a?.invoke() }
    }
    trackPanel.addView(trackList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    overlay.addView(trackPanel, FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    nextView = TextView(this).apply {
      setTextColor(Color.BLACK); textSize = 18f
      setPadding(dp(22), dp(14), dp(22), dp(14))
      background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(Color.WHITE) }
      visibility = View.GONE
    }
    overlay.addView(nextView, FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
      bottomMargin = dp(110); rightMargin = dp(48)
    })
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
      .setConnectTimeoutMs(6000)
      // 3 s ohne ein einziges Byte = Leitung steht: sofort neue Verbindung (Puffer bleibt erhalten)
      .setReadTimeoutMs(3000)
      // misst, wann zuletzt Daten vom Anbieter kamen (Grundlage der Hänger-Erkennung)
      .setTransferListener(object : androidx.media3.datasource.TransferListener {
        override fun onTransferInitializing(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
        override fun onTransferStart(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) { lastBytesAt = SystemClock.uptimeMillis() }
        override fun onBytesTransferred(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean, bytesTransferred: Int) { lastBytesAt = SystemClock.uptimeMillis(); bytesTotal += bytesTransferred }
        override fun onTransferEnd(source: androidx.media3.datasource.DataSource, dataSpec: androidx.media3.datasource.DataSpec, isNetwork: Boolean) {}
      })
      .setKeepPostFor302Redirects(true)
    // Kurze Aussetzer überbrückt der Player selbst (ohne Puffer zu verlieren): schnell und mehrfach neu versuchen
    val retry = object : DefaultLoadErrorHandlingPolicy(2) {
      override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val e = info.exception
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in listOf(401, 403, 404)) return C.TIME_UNSET
        return minOf(info.errorCount * 300L, 800L)
      }
    }
    // Hinweis: FLAG_DETECT_ACCESS_UNITS bewusst NICHT gesetzt. Es zerlegt bei Halbbild-Sendern (1080i, viele deutsche
    // und türkische Kanäle) jedes Bild in zwei, das Bild ruckelt dann und läuft dem Ton davon.
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)).setLoadErrorHandlingPolicy(retry)
  }

  // ---------- Ausfallsicherheit ----------
  private fun httpCode(e: PlaybackException?): Int {
    var c: Throwable? = e?.cause
    while (c != null) { if (c is HttpDataSource.InvalidResponseCodeException) return c.responseCode; c = c.cause }
    return 0
  }
  private fun isOnline(): Boolean = try {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val n = cm.activeNetwork
    n != null && (cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
  } catch (e: Exception) { true }

  private fun showStatus(t: String?) {
    if (t == null) statusView.visibility = View.GONE
    else { statusView.text = t; statusView.visibility = View.VISIBLE }
  }
  private fun later(ms: Long, f: () -> Unit) {
    retryTask?.let { main.removeCallbacks(it) }
    val tok = playToken
    val r = Runnable { retryTask = null; if (tok == playToken && overlay.visibility == View.VISIBLE) f() }
    retryTask = r; main.postDelayed(r, ms)
  }
  // Stream frisch laden (nicht den alten Puffer wiederholen); Filme an derselben Stelle, Live am aktuellen Punkt
  private fun reload(url: String? = null) {
    val p = player ?: return
    val u = url ?: urls.getOrNull(urlIdx) ?: return
    val pos = p.currentPosition
    if (isLive) p.setMediaItem(MediaItem.fromUri(u)) else p.setMediaItem(MediaItem.fromUri(u), pos)
    p.prepare(); p.playWhenReady = true
    stallSince = 0; lastBuffered = 0; lastProgress = SystemClock.uptimeMillis(); loadStartAt = lastProgress
  }

  // Verbindung weg, Anbieter-Fehler oder Hänger: erst schnell neu verbinden, dann andere Adresse (HLS/TS),
  // dann (Live) automatisch eine andere Variante desselben Senders, die dasselbe zeigt.
  private fun recover(err: PlaybackException?) {
    android.util.Log.i("IPFLIX", "Wiederherstellen: " + (err?.errorCodeName ?: "Hänger/Ende") + " Versuch=$attempts url=$urlIdx")
    if (player == null || overlay.visibility != View.VISIBLE) return
    stallSince = 0
    if (!isOnline()) {
      showStatus("Keine Internetverbindung. Die Wiedergabe startet automatisch, sobald sie zurück ist.")
      later(5000) { recover(err) }
      return
    }
    val code = httpCode(err)
    if ((code == 401 || code == 403) && !authRetry) {
      // oft nur die alte Verbindung beim Anbieter noch offen: kurz warten, dann nochmal
      authRetry = true
      showStatus("Verbindung wird wiederhergestellt …")
      later(2000) { reload() }
      return
    }
    // Server antwortet, liefert aber nichts (5xx): schneller zur nächsten Adresse bzw. zum Ersatzsender
    val maxTries = if (isLive) (if (code >= 500) 1 else 2) else 6
    if (code != 401 && code != 403 && code != 404 && attempts < maxTries) {
      attempts++
      showStatus("Verbindung wird wiederhergestellt …")
      later(longArrayOf(250, 1000, 2000, 3000, 4000, 5000)[attempts - 1]) { reload() }
      return
    }
    if (isLive && urlIdx + 1 < urls.size) {
      urlIdx++; attempts = 0
      showStatus("Verbindung wird wiederhergestellt …")
      later(200) { reload(urls[urlIdx]) }
      return
    }
    val msg = if (err != null) friendlyError(err) else "Der Stream hat aufgehört zu senden."
    if (isLive) {
      showStatus("Sender gestört, suche gleichwertigen Ersatz …")
      runJs("window.__player&&__player.failover?__player.failover(" + jsStr(msg) + "):(window.__player&&__player.error&&__player.error(" + jsStr(msg) + "))")
    } else {
      hidePlayer()
      runJs("window.__player&&__player.error&&__player.error(" + jsStr(msg) + ")")
    }
  }

  // Hänger erkennen: Live > 10 s am Puffern, Filme > 20 s -> neu verbinden
  private val watchdog = object : Runnable {
    override fun run() {
      if (overlay.visibility != View.VISIBLE) return
      val p = player
      val now = SystemClock.uptimeMillis()
      // Kommen noch Daten vom Anbieter an? Dann nur warten. Steht die Verbindung (keine neuen Daten), neu verbinden.
      lastProgress = maxOf(lastProgress, lastBytesAt)
      if (DEBUG_LOG && p != null && (now / 1000) % 3 == 0L) android.util.Log.d("IPFLIX", "st=${p.playbackState} playing=${p.isPlaying} pos=${p.currentPosition} buf=${p.bufferedPosition} bytesAgo=${now - lastBytesAt}")
      if (p != null && p.playWhenReady && p.playbackState == Player.STATE_BUFFERING) {
        // erst ab Beginn des Ladens zählen (vorher war der Puffer evtl. voll und es kamen zu Recht keine Daten)
        if (stallSince == 0L) { stallSince = now; lastProgress = maxOf(lastProgress, now) }
        // TS liefert laufend Daten (4 s ohne Daten = tot); HLS kommt in Stücken, dort länger warten
        val ts = urls.getOrNull(urlIdx)?.substringBefore('?')?.endsWith(".ts") == true
        // vor den ersten Daten großzügiger (manche Anbieter brauchen ein paar Sekunden, bis ein Sender anläuft)
        val started = lastBytesAt > loadStartAt
        val dead = now - lastProgress > (if (!started) 9_000 else if (isLive && ts) 4_000 else if (isLive) 12_000 else 8_000)
        val long = now - stallSince > (if (isLive) 20_000 else 30_000)        // kommt zu langsam
        if (dead || long) recover(null)
      } else stallSince = 0
      main.postDelayed(this, 1000)
    }
  }

  // Internet wieder da: sofort weiter (nicht erst auf den nächsten Versuch warten)
  private fun watchNetwork() {
    try {
      val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      val cb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          main.post {
            val p = player ?: return@post
            if (overlay.visibility == View.VISIBLE && (p.playerError != null || p.playbackState != Player.STATE_READY)) {
              retryTask?.let { main.removeCallbacks(it) }; retryTask = null
              attempts = 0; showStatus("Verbindung wird wiederhergestellt …"); reload()
            }
          }
        }
      }
      cm.registerDefaultNetworkCallback(cb)
      netCallback = cb
    } catch (e: Exception) {}
  }
  private var netCallback: ConnectivityManager.NetworkCallback? = null

  // ---------- Tatsächliche Qualität messen ----------
  private fun decodedFrames(): Long = player?.videoDecoderCounters?.let {
    it.ensureUpdated(); (it.renderedOutputBufferCount + it.skippedOutputBufferCount + it.droppedBufferCount).toLong() } ?: -1L
  private fun startQuality() {
    if (qUrl.isEmpty() || qToken == playToken) return
    qToken = playToken
    val tok = playToken
    // erst nach dem Anlaufen messen (der Startpuffer würde die Datenrate verfälschen)
    main.postDelayed({
      if (tok != playToken || qUrl.isEmpty()) return@postDelayed
      qReady = true; qT0 = SystemClock.uptimeMillis(); qB0 = bytesTotal; qF0 = decodedFrames(); qStalls = 0
      qD0 = player?.videoDecoderCounters?.droppedBufferCount ?: 0
      main.postDelayed({ if (tok == playToken) reportQuality(false) }, 20_000)
    }, 8_000)
  }
  // final = Sender wird verlassen: Ergebnis mit Hängern an die Oberfläche (wählt danach die beste Variante)
  private fun reportQuality(final: Boolean) {
    val p = player ?: return
    if (qUrl.isEmpty() || qT0 == 0L) return
    val secs = (SystemClock.uptimeMillis() - qT0) / 1000.0
    if (secs < 15) return
    val f = p.videoFormat
    val w = f?.width ?: -1; val h = f?.height ?: -1
    val frames = decodedFrames()
    // Bildrate aus dem Stream; fehlt sie (oft bei TS), aus den dekodierten Bildern
    val fps = if ((f?.frameRate ?: -1f) > 1f) f!!.frameRate.toDouble() else if (qF0 >= 0 && frames > qF0) (frames - qF0) / secs else -1.0
    val kbps = ((bytesTotal - qB0) * 8 / 1000.0 / secs).toInt()
    val mime = f?.sampleMimeType ?: ""
    val codec = when { mime.contains("hevc") -> "HEVC"; mime.contains("avc") -> "H.264"; mime.contains("mpeg2") -> "MPEG-2"; mime.contains("av01") -> "AV1"; else -> mime.substringAfter('/').uppercase() }
    val dropped = maxOf(0, (p.videoDecoderCounters?.droppedBufferCount ?: 0) - qD0)
    if (!final && h > 0) {
      val res = if (h >= 2000) "4K" else if (h >= 1000) "1080" else if (h >= 700) "720" else "${h}"
      val fpsTxt = if (fps > 10) " · ${Math.round(fps)} fps" else ""
      qualityView.text = "Empfangen: ${res}${if (res == "4K") "" else "p"}$fpsTxt · $codec · ${"%.1f".format(kbps / 1000.0).replace('.', ',')} Mbit/s"
      qualityView.visibility = View.VISIBLE
    }
    val js = org.json.JSONObject().apply {
      put("u", qUrl); put("w", w); put("h", h); put("fps", Math.round(fps * 10) / 10.0); put("kbps", kbps)
      put("codec", codec); put("stalls", qStalls); put("secs", Math.round(secs)); put("dropped", dropped); put("final", final)
    }
    if (final) { qT0 = 0L; qReady = false }
    runJs("window.__player&&__player.quality&&__player.quality(" + org.json.JSONObject.quote(js.toString()) + ")")
  }

  // ---------- Bildwiederholrate (ruckelfreie Bewegungen, z. B. Fußball mit 50 Bildern/s) ----------
  private fun measureFps() {
    val p = player ?: return
    val tok = playToken
    if (!afrEnabled || fpsToken == tok) return
    fpsToken = tok
    val f = p.videoFormat?.frameRate ?: -1f
    if (f > 10f) { applyFps(f); return }
    fun frames(): Long = p.videoDecoderCounters?.let {
      it.ensureUpdated(); (it.renderedOutputBufferCount + it.skippedOutputBufferCount + it.droppedBufferCount).toLong() } ?: -1L
    val c0 = frames(); val t0 = SystemClock.uptimeMillis()
    main.postDelayed({
      if (tok != playToken || overlay.visibility != View.VISIBLE || !p.isPlaying) { fpsToken = -1; return@postDelayed }
      val c1 = frames()
      if (c0 >= 0 && c1 > c0) applyFps((c1 - c0) * 1000f / (SystemClock.uptimeMillis() - t0))
    }, 4000)
  }
  private fun applyFps(raw: Float) {
    if (!afrEnabled || android.os.Build.VERSION.SDK_INT < 23) return
    val std = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
    val fps = std.minByOrNull { Math.abs(it - raw) } ?: return
    if (Math.abs(fps - raw) > 1.5f) return
    if (isLive && fps != 25f && fps != 50f) return
    // gewünschte Rate: 24p als 24 Hz, 25/30p doppelt (50/60 Hz), 50/60p direkt
    val want = if (fps < 24.5f || fps >= 49f) fps else fps * 2
    @Suppress("DEPRECATION") val d = windowManager.defaultDisplay
    val cur = d.mode
    val m = d.supportedModes
      .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
      .minByOrNull { Math.abs(it.refreshRate - want) } ?: return
    if (Math.abs(m.refreshRate - want) > 0.1f || m.modeId == cur.modeId) return
    if (!modeChanged) origModeId = cur.modeId
    modeChanged = true
    window.attributes = window.attributes.also { it.preferredDisplayModeId = m.modeId }
  }
  private val restoreLater = Runnable { restoreMode() }
  private fun restoreMode() {
    if (!modeChanged) return
    modeChanged = false
    window.attributes = window.attributes.also { it.preferredDisplayModeId = origModeId }
  }

  private val playerListener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
      val p = player
      // Live zu lange pausiert ("hinter dem Live-Fenster"): einfach wieder am aktuellen Live-Punkt einsteigen
      if (p != null && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        p.seekToDefaultPosition(); p.prepare(); p.playWhenReady = true
        return
      }
      // Format/Decoder-Fehler: anderer Weg (TS statt HLS) kann helfen, Wiederholen nicht
      val decode = error.errorCode in 4000..4999 || error.errorCode in 3000..3999
      if (decode && !(isLive && urlIdx + 1 < urls.size)) attempts = 99
      recover(error)
    }

    // Erstes Bild laeuft: erst ab jetzt zaehlt die Sehzeit fuer "Deine Sender"
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (isPlaying) {
        runJs("window.__player&&__player.playing&&__player.playing()")
        showStatus(null)
        measureFps()
        startQuality()
        // stabil gelaufen: Zähler für Wiederholversuche zurücksetzen
        val tok = playToken
        main.postDelayed({ if (tok == playToken && player?.isPlaying == true) { attempts = 0; authRetry = false } }, 15_000)
      }
    }

    override fun onPlaybackStateChanged(state: Int) {
      // Protokoll (logcat "IPFLIX"): Pufferphasen mit Dauer, um Hänger zu messen
      val now = SystemClock.uptimeMillis()
      if (state == Player.STATE_BUFFERING) { bufStart = now; if (qReady && isLive) qStalls++ }
      if (state == Player.STATE_READY && bufStart > 0) {
        android.util.Log.i("IPFLIX", "Puffern beendet nach ${now - bufStart} ms (live=$isLive)"); bufStart = 0
      }
      if (state == Player.STATE_READY) startTimer()
      if (state == Player.STATE_ENDED) {
        if (isLive) {
          // Live-Stream bricht ab (Anbieter trennt die Verbindung): neu verbinden statt aufhören
          recover(null)
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
      // Untertitel nicht automatisch einschalten (nur erzwungene, z. B. für fremdsprachige Stellen);
      // einschalten/ausschalten über den Untertitel-Knopf in der Leiste
      .setPreferredTextLanguages()
      .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
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
    // Adresse des Anbieters vorab auflösen (DNS), damit der erste Sender schneller startet
    @JavascriptInterface fun warm(url: String) {
      Thread { try { java.net.InetAddress.getAllByName(URL(url).host) } catch (e: Exception) {} }.start()
    }
    // Ton-Versatz in ms: positiv = Ton später, negativ = Ton früher
    @JavascriptInterface fun avOffset(ms: Int) { main.post { setAvOffset(ms) } }
    // Ton-Test: Testbild mit Blitz und Piepton (exakt gleichzeitig), Versatz mit links/rechts einstellen
    @JavascriptInterface fun syncTest() { main.post { startSyncTest() } }
    @JavascriptInterface fun browse(url: String) { main.post { openBrowser(url) } }
    // Senderstatus ohne Abspielen: Stream kurz öffnen und messen, wie schnell und gleichmäßig Daten kommen
    @JavascriptInterface fun probe(id: String, url: String) {
      probeExec.execute {
        val r = doProbe(url)
        runJs("window.__player&&__player.probe&&__player.probe(" + jsStr(id) + "," + jsStr(r) + ")")
      }
    }
    @JavascriptInterface fun syncStats(): String = syncMeter.last
    @JavascriptInterface fun afr(on: Boolean) { main.post { afrEnabled = on; if (!on) restoreMode() } }
    // Beim Einschalten starten: "on", "off", oder "perm" (Erlaubnis fehlt noch)
    @JavascriptInterface fun autostartState(): String =
      if (!AutoStart.enabled(this@MainActivity)) "off" else if (AutoStart.allowed(this@MainActivity)) "on" else "perm"
    @JavascriptInterface fun autostart(on: Boolean): String {
      AutoStart.set(this@MainActivity, on)
      if (!on) return "off"
      if (AutoStart.allowed(this@MainActivity)) return "on"
      return if (openOverlaySettings()) "perm" else "manual"
    }
    @JavascriptInterface fun stop() { main.post { hidePlayer() } }
    // Senderliste: [[Nummer, Name, Variante], ...], aktuelle Position, Name des vorherigen Senders
    @JavascriptInterface fun channels(json: String, idx: Int, prev: String) {
      val items = try {
        val a = org.json.JSONArray(json)
        (0 until a.length()).map { i ->
          val r = a.getJSONArray(i)
          val num = r.optString(0); val name = r.optString(1); val q = r.optString(2)
          (if (num.isNotEmpty()) "$num   " else "") + name + (if (q.isNotEmpty()) "   ·  $q" else "")
        }
      } catch (e: Exception) { emptyList() }
      main.post { chanItems = items; chanIdx = maxOf(0, idx); prevName = prev; chanHasPrev = prev.isNotEmpty() }
    }
    @JavascriptInterface fun notice(text: String) { main.post { if (overlay.visibility == View.VISIBLE) notice(text, 5000) } }
    @JavascriptInterface fun next(label: String) { main.post { nextLabel = label } }
    @JavascriptInterface fun voice(lang: String) { main.post { startVoice(lang) } }
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
    reportQuality(true)
    endSyncTest()
    // laufende Senderprüfung sofort beenden: viele Anbieter erlauben nur eine Verbindung
    playerOpen = true
    probeConn?.let { c -> Thread { try { c.disconnect() } catch (e: Exception) {} }.start() }
    p.repeatMode = Player.REPEAT_MODE_OFF
    qUrl = if (live) url else ""; qReady = false; qT0 = 0L; qStalls = 0
    qualityView.text = ""; qualityView.visibility = View.GONE
    playToken++
    isLive = live
    urls = listOf(url, alt).filter { it.isNotEmpty() }.distinct()
    urlIdx = 0; attempts = 0; authRetry = false; stallSince = 0
    lastBuffered = 0; lastProgress = SystemClock.uptimeMillis(); loadStartAt = lastProgress
    retryTask?.let { main.removeCallbacks(it) }; retryTask = null
    showStatus(null)
    titleView.text = title
    // Zeitbalken mit der Fernbedienung: links/rechts verschiebt um 10 s
    playerView.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.setKeyTimeIncrement(10_000)
    main.removeCallbacks(restoreLater)
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
    main.removeCallbacks(watchdog); main.postDelayed(watchdog, 1000)
    hideChannels(); hideTracks(); nextLabel = ""; nextView.visibility = View.GONE
    // jeder neue Titel startet ohne Untertitel (nur erzwungene); eine Auswahl gilt nur für den laufenden Titel
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .clearOverridesOfType(C.TRACK_TYPE_TEXT).clearOverridesOfType(C.TRACK_TYPE_AUDIO)
      .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    main.removeCallbacks(nextTick); main.postDelayed(nextTick, 1000)
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

  private val endSkip = Runnable { skipActive = false; skipSum = 0; skipView.visibility = View.GONE }
  // In den Knöpfen der Steuerleiste bewegen (Zahnrad: Tonspur/Untertitel)
  private fun focusButtons() {
    playerView.showController()
    (playerView.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause))?.requestFocus()
  }
  private fun skip(dir: Int, sec: Int = 10) {
    val p = player ?: return
    if (!skipActive || skipDir != dir) { skipSum = 0 }
    skipActive = true; skipDir = dir
    val dur = if (p.duration > 0) p.duration else Long.MAX_VALUE
    val target = (p.currentPosition + dir * sec * 1000L).coerceIn(0L, maxOf(0L, dur - 1_000L))
    p.seekTo(target)
    skipSum += sec
    val t = if (skipSum >= 60) "${skipSum / 60}:${"%02d".format(skipSum % 60)} Min." else "$skipSum Sek."
    skipView.text = if (dir > 0) "$t  »" else "«  $t"
    (skipView.layoutParams as FrameLayout.LayoutParams).apply {
      gravity = Gravity.CENTER_VERTICAL or (if (dir > 0) Gravity.END else Gravity.START)
      setMargins(dp(90), 0, dp(90), 0)
      skipView.layoutParams = this
    }
    skipView.visibility = View.VISIBLE
    playerView.showController()       // Fortschrittsbalken mit Zeit sichtbar, wie bei Netflix
    main.removeCallbacks(endSkip); main.postDelayed(endSkip, 1300)
  }

  // ---------- Senderliste ----------
  private val hideChans = Runnable { hideChannels() }
  private fun bumpChanTimer() { main.removeCallbacks(hideChans); main.postDelayed(hideChans, 20_000) }
  private fun showChannels() {
    if (chanItems.isEmpty()) { playerView.showController(); return }
    val rows = (if (chanHasPrev) listOf("↩  Vorheriger Sender: $prevName") else emptyList()) + chanItems
    chanList.adapter = object : android.widget.BaseAdapter() {
      override fun getCount() = rows.size
      override fun getItem(i: Int) = rows[i]
      override fun getItemId(i: Int) = i.toLong()
      override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
        val tv = (cv as? TextView) ?: TextView(this@MainActivity).apply {
          textSize = 17f; setPadding(dp(14), dp(11), dp(14), dp(11)); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        tv.text = rows[i]
        val cur = i == chanIdx + (if (chanHasPrev) 1 else 0)
        tv.setTextColor(if (cur) 0xFFE50914.toInt() else Color.WHITE)
        tv.setTypeface(null, if (cur) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        return tv
      }
    }
    playerView.hideController()
    chanPanel.visibility = View.VISIBLE
    chanList.requestFocus()
    chanList.setSelection(chanIdx + (if (chanHasPrev) 1 else 0))
    bumpChanTimer()
  }
  private fun hideChannels() {
    main.removeCallbacks(hideChans)
    if (chanPanel.visibility != View.VISIBLE) return
    chanPanel.visibility = View.GONE
    playerView.requestFocus()
  }
  private fun pickChannel(pos: Int) {
    hideChannels()
    if (chanHasPrev && pos == 0) runJs("window.__player&&__player.prev&&__player.prev()")
    else runJs("window.__player&&__player.pick&&__player.pick(" + (pos - (if (chanHasPrev) 1 else 0)) + ")")
  }
  private var prevName = ""
  private val digitGo = Runnable {
    val n = digits; digits = ""
    if (n.isNotEmpty()) runJs("window.__player&&__player.num&&__player.num($n)")
  }
  private val noticeOff = Runnable { showStatus(null) }
  private fun notice(t: String, ms: Long = 4000) { showStatus(t); main.removeCallbacks(noticeOff); main.postDelayed(noticeOff, ms) }

  // ---------- Untertitel / Tonspur ----------
  private fun langName(code: String?): String {
    if (code.isNullOrEmpty() || code == "und") return "Unbekannt"
    return try { java.util.Locale(code).getDisplayLanguage(java.util.Locale.GERMAN).replaceFirstChar { it.uppercase() } } catch (e: Exception) { code }
  }
  private fun showTracks(type: Int) {
    val p = player ?: return
    val rows = mutableListOf<String>(); val acts = mutableListOf<() -> Unit>()
    var sel = 0
    val textOff = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    if (type == C.TRACK_TYPE_TEXT) {
      rows += "Aus"; acts += {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
          .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
      }
    }
    var anySelected = false
    for (g in p.currentTracks.groups) {
      if (g.type != type) continue
      for (i in 0 until g.length) {
        if (!g.isTrackSupported(i)) continue
        val f = g.getTrackFormat(i)
        var label = langName(f.language)
        if (!f.label.isNullOrEmpty() && f.label != f.language) label += " · " + f.label
        if (type == C.TRACK_TYPE_AUDIO && f.channelCount > 0) label += when (f.channelCount) { 1 -> " · Mono"; 2 -> " · Stereo"; 6 -> " · 5.1"; 8 -> " · 7.1"; else -> " · ${f.channelCount} Kanäle" }
        if ((f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0) label += " (erzwungen)"
        val on = g.isTrackSelected(i) && !(type == C.TRACK_TYPE_TEXT && textOff)
        if (on) { sel = rows.size; anySelected = true }
        rows += label
        val mg = g.mediaTrackGroup; val idx = i
        acts += {
          p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(mg, idx)).build()
        }
      }
    }
    if (type == C.TRACK_TYPE_TEXT && !anySelected) sel = 0
    if (rows.size <= (if (type == C.TRACK_TYPE_TEXT) 1 else 0)) {
      notice(if (type == C.TRACK_TYPE_TEXT) "Dieser Titel hat keine Untertitel." else "Nur eine Tonspur vorhanden.", 3000); return
    }
    trackTitle.text = if (type == C.TRACK_TYPE_TEXT) "Untertitel" else "Tonspur"
    trackActions = acts
    val chosen = sel
    trackList.adapter = object : android.widget.BaseAdapter() {
      override fun getCount() = rows.size
      override fun getItem(i: Int) = rows[i]
      override fun getItemId(i: Int) = i.toLong()
      override fun getView(i: Int, cv: View?, parent: ViewGroup?): View {
        val tv = (cv as? TextView) ?: TextView(this@MainActivity).apply { textSize = 17f; setPadding(dp(14), dp(11), dp(14), dp(11)) }
        tv.text = (if (i == chosen) "✓  " else "     ") + rows[i]
        tv.setTextColor(if (i == chosen) 0xFFE50914.toInt() else Color.WHITE)
        return tv
      }
    }
    playerView.hideController()
    trackPanel.visibility = View.VISIBLE
    trackList.requestFocus()
    trackList.setSelection(sel)
  }
  private fun hideTracks() {
    if (trackPanel.visibility != View.VISIBLE) return
    trackPanel.visibility = View.GONE
    playerView.requestFocus()
  }

  // ---------- nächste Folge ----------
  private val nextTick = object : Runnable {
    override fun run() {
      if (overlay.visibility != View.VISIBLE) return
      val p = player
      if (p != null && nextLabel.isNotEmpty() && !isLive && p.duration > 0) {
        val left = (p.duration - p.currentPosition) / 1000
        if (left in 0..20 && p.playbackState != Player.STATE_ENDED) {
          nextView.text = "Nächste Folge in $left s  ·  $nextLabel\nOK = jetzt ansehen"
          nextView.visibility = View.VISIBLE
        } else nextView.visibility = View.GONE
      }
      main.postDelayed(this, 1000)
    }
  }

  private fun endSyncTest() {
    if (!syncTest) return
    syncTest = false; syncMeter.active = false
    main.removeCallbacks(syncTick)
    player?.repeatMode = Player.REPEAT_MODE_OFF
    runJs("window.__player&&__player.avOffset&&__player.avOffset($avOffsetMs)")
  }
  private fun hidePlayer() {
    reportQuality(true)
    endSyncTest()
    playerOpen = false
    qUrl = ""
    playToken++
    main.removeCallbacks(endSkip); endSkip.run()
    hideChannels(); hideTracks(); nextView.visibility = View.GONE; nextLabel = ""; main.removeCallbacks(nextTick)
    retryTask?.let { main.removeCallbacks(it) }; retryTask = null
    main.removeCallbacks(watchdog); showStatus(null)
    // Bildrate erst nach einer Minute ohne Wiedergabe zurückstellen: beim Umschalten kein erneutes Schwarzbild
    main.removeCallbacks(restoreLater); main.postDelayed(restoreLater, 60_000); fpsToken = -1
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
    // Eingebauter Browser (YouTube-Trailer): hoch/runter springt von Link zu Link, Zurück geht zurück bzw. schließt
    browser?.let { b ->
      when (k) {
        KeyEvent.KEYCODE_BACK -> {
          if (down && event.repeatCount == 0) { if (b.canGoBack()) b.goBack() else closeBrowser() }
          return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
          val shift = if (k == KeyEvent.KEYCODE_DPAD_UP) KeyEvent.META_SHIFT_ON else 0
          b.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_TAB, event.repeatCount, shift))
          return true
        }
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return super.dispatchKeyEvent(event)
        else -> { b.dispatchKeyEvent(event); return true }
      }
    }
    if (overlay.visibility == View.VISIBLE) {
      // Lautstaerke & Co. bleiben beim System
      if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
        return super.dispatchKeyEvent(event)
      }
      val p = player
      // Ton-Test: links/rechts verschiebt den Ton in 10-ms-Schritten, hoch/runter in 50 ms
      if (syncTest) {
        val step = when (k) { KeyEvent.KEYCODE_DPAD_LEFT -> -10; KeyEvent.KEYCODE_DPAD_RIGHT -> 10; KeyEvent.KEYCODE_DPAD_DOWN -> -50; KeyEvent.KEYCODE_DPAD_UP -> 50; else -> 0 }
        if (step != 0) {
          if (down) { setAvOffset(avOffsetMs + step); showSyncInfo() }
          return true
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
          if (!down) { setAvOffset(0); showSyncInfo() }
          return true
        }
      }
      // Leiste sichtbar (auch während sie noch einblendet); "fully visible" ist beim Einblenden noch falsch
      val ctrl = playerView.findViewById<View>(androidx.media3.ui.R.id.exo_controller)?.visibility == View.VISIBLE
      val f = playerView.findFocus()
      val skipping = skipActive && !isLive && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)
      val inButtons = ctrl && f != null && f !== playerView && !skipping     // Nutzer bewegt sich in den Knöpfen (z. B. Zahnrad für Tonspur)
      val first = down && event.repeatCount == 0
      if (DEBUG_LOG) android.util.Log.d("IPFLIX", "key=$k down=$down list=${chanPanel.visibility} focus=${window.currentFocus?.javaClass?.simpleName} ctrl=$ctrl inButtons=$inButtons")
      // Untertitel-/Tonspur-Menü offen: Android bewegt die Auswahl; Zurück schließt es
      if (trackPanel.visibility == View.VISIBLE) {
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_RIGHT) { if (first) hideTracks(); return true }
        val okKey = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (okKey && swallowOkUp) { if (!down) swallowOkUp = false; return true }
        return super.dispatchKeyEvent(event)
      }
      // Senderliste offen: Android bewegt die Auswahl; Zurück/links schließt sie
      if (chanPanel.visibility == View.VISIBLE) {
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_DPAD_LEFT) { if (first) hideChannels(); return true }
        // das Loslassen der OK-Taste, die die Liste geöffnet hat, darf keinen Sender auswählen
        val okKey = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (okKey && swallowOkUp) { if (!down) swallowOkUp = false; return true }
        bumpChanTimer()
        return super.dispatchKeyEvent(event)
      }
      // Sendernummer eintippen (Fernbedienungen mit Zifferntasten)
      if (isLive && k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) {
        if (first && digits.length < 4) {
          digits += (k - KeyEvent.KEYCODE_0).toString()
          notice("Sender $digits", 1800)
          main.removeCallbacks(digitGo); main.postDelayed(digitGo, 1500)
        }
        return true
      }
      if (isLive && k == KeyEvent.KEYCODE_LAST_CHANNEL) { if (first) runJs("window.__player&&__player.prev&&__player.prev()"); return true }
      if (k == KeyEvent.KEYCODE_BACK) {
        if (first) {
          if (inButtons) { playerView.hideController(); playerView.requestFocus() }   // PlayerView selbst, nicht ein Knopf
          else { reportTime(); hidePlayer(); runJs("window.__player&&__player.back&&__player.back()") }
        }
        return true
      }
      if (first) {
        when (k) {
          KeyEvent.KEYCODE_CHANNEL_UP -> { runJs("window.__player&&__player.switch&&__player.switch(1)"); return true }
          KeyEvent.KEYCODE_CHANNEL_DOWN -> { runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true }
          KeyEvent.KEYCODE_MEDIA_NEXT -> if (isLive) { runJs("window.__player&&__player.switch&&__player.switch(1)"); return true }
          KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (isLive) { runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true }
        }
      }
      // In den Knöpfen: Android selbst die Auswahl bewegen lassen (links/rechts/hoch/runter zwischen Knöpfen,
      // Zeitbalken und Zahnrad). Vorher wurde jede Taste hier "verschluckt", dadurch blieb die Auswahl hängen.
      if (inButtons && f != null) {
        // Der Player fängt Pfeiltasten ab, solange seine Leiste noch einblendet (bei laufendem Film ständig).
        // Deshalb bewegt die App die Auswahl selbst: zwischen Knöpfen, Zeitbalken und Zahnrad.
        val dir = when (k) {
          KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
          KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP; KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN; else -> 0
        }
        if (dir != 0) {
          if (down) {
            playerView.showController()                     // Leiste bleibt offen, solange bedient wird
            if (f is androidx.media3.ui.DefaultTimeBar && (dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT)) {
              f.onKeyDown(k, event)                         // Zeitbalken: links/rechts = 10 s schieben
            } else {
              val nx = android.view.FocusFinder.getInstance().findNextFocus(playerView, f, dir)
              if (DEBUG_LOG) android.util.Log.d("IPFLIX", "nav dir=$dir from=${resources.getResourceEntryName(f.id.takeIf { it > 0 } ?: android.R.id.content)} to=${nx?.let { if (it.id > 0) resources.getResourceEntryName(it.id) else it.javaClass.simpleName }}")
              nx?.requestFocus()
            }
          }
          return true
        }
        val okKey = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (okKey) {
          if (first) {
            when (f.id) {
              androidx.media3.ui.R.id.exo_subtitle -> { swallowOkUp = true; showTracks(C.TRACK_TYPE_TEXT) }
              androidx.media3.ui.R.id.exo_settings -> { swallowOkUp = true; showTracks(C.TRACK_TYPE_AUDIO) }
              else -> { playerView.showController(); if (f !is androidx.media3.ui.DefaultTimeBar) f.performClick() }
            }
          }
          return true
        }
        return super.dispatchKeyEvent(event)
      }
      val isOk = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                 k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_SPACE
      when (k) {
        KeyEvent.KEYCODE_DPAD_UP -> {
          if (first) { if (isLive && !ctrl) runJs("window.__player&&__player.switch&&__player.switch(1)") else playerView.showController() }
          return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
          if (first) { if (isLive && !ctrl) runJs("window.__player&&__player.switch&&__player.switch(-1)") else focusButtons() }
          return true
        }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
          if (down) {
            // Live: nur spulen, wenn der Anbieter ein Zeitfenster liefert; sonst Leiste zeigen
            if (isLive && p?.isCurrentMediaItemSeekable != true) { if (first) playerView.showController() }
            else {
              // Filme/Serien: jeder Druck 10 s; gedrückt halten spult immer schneller (30 s, dann 60 s)
              val now = SystemClock.uptimeMillis()
              if (first || now - lastTap >= 220) {
                val dir = if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) 1 else -1
                val step = when { event.repeatCount >= 25 -> 60; event.repeatCount >= 8 -> 30; else -> 10 }
                skip(dir, step); lastTap = now
              }
            }
          }
          return true
        }
        KeyEvent.KEYCODE_MEDIA_PLAY -> { if (first) { p?.play(); playerView.showController() }; return true }
        KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (first) { p?.pause(); playerView.showController() }; return true }
      }
      if (isOk) {
        // Countdown "Nächste Folge" sichtbar: OK startet sie sofort
        if (first && nextView.visibility == View.VISIBLE && k != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
          nextView.visibility = View.GONE; nextLabel = ""
          runJs("window.__player&&__player.next&&__player.next()"); return true
        }
        // OK: Live = Senderliste öffnen; Filme = Pause/Weiter
        if (first && p != null) {
          if (isLive && !ctrl && k != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { swallowOkUp = true; showChannels() }
          else { if (p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)) p.pause() else p.play(); playerView.showController() }
        }
        return true
      }
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
  override fun onStop() {
    super.onStop()
    main.removeCallbacks(restoreLater); restoreMode()   // App verlassen: Fernseher wieder auf die normale Bildrate
  }
  override fun onResume() {
    super.onResume()
    AutoStart.startWatcher(this)
    runJs("window.__player&&__player.shown&&__player.shown()")
    val p = player
    // Live nach der Rückkehr frisch verbinden (die alte Verbindung ist oft schon weg)
    if (p != null && isLive && overlay.visibility == View.VISIBLE) reload()
  }

  // Einstellungsseite „Über anderen Apps einblenden“ für IPFLIX öffnen (nötig für den Autostart)
  private fun openOverlaySettings(): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 23) return true
    val uri = android.net.Uri.parse("package:$packageName")
    val tries = listOf(
      Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri),
      Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
      Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    for (i in tries) {
      try {
        if (i.resolveActivity(packageManager) == null) continue
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
      } catch (e: Exception) {}
    }
    return false
  }

  // ---------- Sprachsuche ----------
  private fun startVoice(lang: String) {
    val tag = when (lang) { "tr" -> "tr-TR"; "en" -> "en-US"; "fr" -> "fr-FR"; "es" -> "es-ES"; "it" -> "it-IT"; "pl" -> "pl-PL"
                             "nl" -> "nl-NL"; "pt" -> "pt-PT"; "ru" -> "ru-RU"; "ar" -> "ar-SA"; else -> "de-DE" }
    val i = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, tag)
      putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Film, Serie oder Sender sagen")
    }
    try { @Suppress("DEPRECATION") startActivityForResult(i, VOICE_REQ) }
    catch (e: Exception) { runJs("window.toast&&toast('Sprachsuche ist auf diesem Gerät nicht verfügbar. Tipp: das Mikrofon auf der Bildschirmtastatur nutzen.',true)") }
  }
  @Deprecated("startActivityForResult")
  override fun onActivityResult(req: Int, res: Int, data: Intent?) {
    @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
    if (req != VOICE_REQ || res != RESULT_OK) return
    val text = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
    runJs("window.__player&&__player.voice&&__player.voice(" + jsStr(text) + ")")
  }

  override fun onDestroy() {
    closeBrowser()
    main.removeCallbacksAndMessages(null)
    player?.release(); player = null
    try { webView.destroy() } catch (e: Exception) {}
    netCallback?.let { try { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) } catch (e: Exception) {} }
    netCallback = null
    super.onDestroy()
  }

  companion object {
    const val UA = "IPTVSmartersPro"
    const val VOICE_REQ = 42
    const val LIVE_START_US = 2_500_000L
    const val LIVE_REBUFFER_US = 4_000_000L
    const val DEBUG_LOG = false
    private var lastUiGone = -100000L
  }

  private fun matchParent() = FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
