package com.ipflix.app

import android.annotation.SuppressLint
import android.graphics.Color
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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
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
  private var player: ExoPlayer? = null
  private val main = Handler(Looper.getMainLooper())

  private var altUrl = ""
  private var triedAlt = false
  private var isLive = false
  private var playToken = 0

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(saved: Bundle?) {
    super.onCreate(saved)
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
    webView.webViewClient = WebViewClient()
    webView.webChromeClient = WebChromeClient()
    webView.setBackgroundColor(Color.BLACK)
    webView.addJavascriptInterface(Bridge(), "NativePlayer")
    root.addView(webView, matchParent())

    player = ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory()).build().also {
      it.addListener(playerListener)
    }
    playerView = PlayerView(this).apply {
      player = this@MainActivity.player
      useController = true
      controllerShowTimeoutMs = 3500
      resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
      setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
      setBackgroundColor(Color.BLACK)
    }
    overlay = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(Color.BLACK)
    }
    overlay.addView(playerView, matchParent())

    val bar = LinearLayout(this).apply {
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

    setContentView(root)
    webView.loadUrl("file:///android_asset/web/index.html")
  }

  private fun mediaSourceFactory(): DefaultMediaSourceFactory {
    val http = DefaultHttpDataSource.Factory()
      .setUserAgent("IPTVSmartersPro")
      .setAllowCrossProtocolRedirects(true)
      .setConnectTimeoutMs(15000)
      .setReadTimeoutMs(15000)
    return DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))
  }

  private val playerListener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
      if (altUrl.isNotEmpty() && !triedAlt) {
        triedAlt = true
        player?.let { p ->
          p.setMediaItem(MediaItem.fromUri(altUrl)); p.prepare(); p.playWhenReady = true
        }
      } else {
        runJs("window.__player&&__player.error&&__player.error(" + jsStr(error.errorCodeName) + ")")
        hidePlayer()
      }
    }

    override fun onPlaybackStateChanged(state: Int) {
      if (state == Player.STATE_READY) startTimer()
      if (state == Player.STATE_ENDED) {
        val tok = playToken
        runJs("window.__player&&__player.ended&&__player.ended()")
        // Spielt die Oberflaeche nichts Neues an (z. B. Film zu Ende), Player schliessen.
        main.postDelayed({
          if (playToken == tok && player?.playbackState == Player.STATE_ENDED) hidePlayer()
        }, 1500)
      }
    }
  }

  inner class Bridge {
    @JavascriptInterface fun isApp(): Boolean = true
    @JavascriptInterface fun play(url: String, live: Boolean, title: String, startSec: Int, alt: String) {
      main.post { showPlayer(url, live, title, startSec, alt) }
    }
    @JavascriptInterface fun stop() { main.post { hidePlayer() } }
    @JavascriptInterface fun subtitle(text: String) {
      main.post {
        subtitleView.text = text
        subtitleView.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
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
    playerView.requestFocus()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    startTimer()
  }

  private fun hidePlayer() {
    playToken++
    player?.let { it.playWhenReady = false; it.stop(); it.clearMediaItems() }
    overlay.visibility = View.GONE
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    webView.requestFocus()
  }

  private val ticker = object : Runnable {
    override fun run() {
      val p = player
      if (p != null && overlay.visibility == View.VISIBLE && p.isPlaying) {
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
    if (overlay.visibility == View.VISIBLE) {
      if (event.action == KeyEvent.ACTION_DOWN) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_BACK -> {
            hidePlayer(); runJs("window.__player&&__player.back&&__player.back()"); return true
          }
          KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> {
            runJs("window.__player&&__player.switch&&__player.switch(1)"); return true
          }
          KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
            runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true
          }
          KeyEvent.KEYCODE_DPAD_UP -> if (isLive) {
            runJs("window.__player&&__player.switch&&__player.switch(1)"); return true
          }
          KeyEvent.KEYCODE_DPAD_DOWN -> if (isLive) {
            runJs("window.__player&&__player.switch&&__player.switch(-1)"); return true
          }
        }
      }
      return super.dispatchKeyEvent(event)
    }
    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
      webView.evaluateJavascript("(window.__appBack&&window.__appBack())?'1':'0'") { r ->
        if (r == null || r.trim('"') != "1") finish()
      }
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onPause() { super.onPause(); player?.pause() }

  override fun onDestroy() {
    main.removeCallbacksAndMessages(null)
    player?.release(); player = null
    webView.destroy()
    super.onDestroy()
  }

  private fun matchParent() = FrameLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
