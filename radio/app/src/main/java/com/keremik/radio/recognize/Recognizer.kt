package com.keremik.radio.recognize

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.keremik.radio.data.Http
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface RecognizeResult {
  data class Found(val song: Song) : RecognizeResult
  data object NotFound : RecognizeResult
  data object LimitReached : RecognizeResult
  data class Failed(val message: String) : RecognizeResult
}

/** Musikerkennung über den Dienst AudD (audd.io). Gesendet wird nur ein kurzer Tonausschnitt. */
object Recognizer {
  private const val TARGET_RATE = 22_050
  const val SECONDS = 10

  /** Nimmt [SECONDS] Sekunden über das Mikrofon auf (Mono, 16 bit). [onProgress] bekommt 0..1. */
  @SuppressLint("MissingPermission")
  suspend fun recordMic(onProgress: (Float) -> Unit): Pair<ShortArray, Int> = withContext(Dispatchers.IO) {
    val rate = 44_100
    val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
    val total = rate * SECONDS
    val out = ShortArray(total)
    try {
      if (rec.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Mikrofon nicht verfügbar")
      rec.startRecording()
      var n = 0
      while (n < total) {
        currentCoroutineContext().ensureActive()
        val r = rec.read(out, n, minOf(4096, total - n))
        if (r < 0) throw IllegalStateException("Mikrofon-Fehler $r")
        n += r
        onProgress(n.toFloat() / total)
      }
    } finally {
      runCatching { rec.stop() }
      rec.release()
    }
    out to rate
  }

  suspend fun identify(pcm: ShortArray, rate: Int): RecognizeResult = withContext(Dispatchers.IO) {
    try {
      val wav = toWav(resample(pcm, rate, TARGET_RATE), TARGET_RATE)
      val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .apply { Prefs.auddToken.takeIf { it.isNotBlank() }?.let { addFormDataPart("api_token", it) } }
        .addFormDataPart("return", "apple_music,spotify")
        .addFormDataPart("file", "sample.wav", wav.toRequestBody("audio/wav".toMediaType()))
        .build()
      val req = Request.Builder().url("https://api.audd.io/").post(body).header("User-Agent", Http.USER_AGENT).build()
      Http.client.newCall(req).execute().use { resp ->
        val text = resp.body.string()
        if (!resp.isSuccessful) return@withContext RecognizeResult.Failed("HTTP ${resp.code}")
        parse(JSONObject(text))
      }
    } catch (e: Exception) {
      RecognizeResult.Failed(e.message ?: e.javaClass.simpleName)
    }
  }

  private fun parse(o: JSONObject): RecognizeResult {
    if (o.optString("status") != "success") {
      val err = o.optJSONObject("error")
      val code = err?.optInt("error_code") ?: 0
      // 900–902: kein/ungültiger Schlüssel oder Tageslimit erreicht.
      return if (code in 900..902) RecognizeResult.LimitReached
      else RecognizeResult.Failed(err?.optString("error_message")?.take(120) ?: "unbekannt")
    }
    val r = o.optJSONObject("result") ?: return RecognizeResult.NotFound
    val artist = r.optString("artist")
    val title = r.optString("title")
    if (artist.isBlank() && title.isBlank()) return RecognizeResult.NotFound
    val artwork = r.optJSONObject("apple_music")?.optJSONObject("artwork")?.optString("url")
      ?.replace("{w}", "600")?.replace("{h}", "600")?.takeIf { it.startsWith("http") }
      ?: r.optJSONObject("spotify")?.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url")
      ?: ""
    return RecognizeResult.Found(Song(artist, title, r.optString("album"), artwork, System.currentTimeMillis()))
  }

  /** Einfaches Herunterrechnen mit Mittelwert (reicht für die Erkennung völlig). */
  private fun resample(src: ShortArray, from: Int, to: Int): ShortArray {
    if (from <= to) return src
    val ratio = from.toDouble() / to
    val n = (src.size / ratio).toInt()
    val out = ShortArray(n)
    for (i in 0 until n) {
      val a = (i * ratio).toInt()
      val b = minOf(src.size, ((i + 1) * ratio).toInt()).coerceAtLeast(a + 1)
      var sum = 0L
      for (j in a until b) sum += src[j]
      out[i] = (sum / (b - a)).toInt().toShort()
    }
    return out
  }

  private fun toWav(pcm: ShortArray, rate: Int): ByteArray {
    val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    pcm.forEach { data.putShort(it) }
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size * 2); h.put("WAVE".toByteArray())
    h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(rate); h.putInt(rate * 2)
    h.putShort(2); h.putShort(16)
    h.put("data".toByteArray()); h.putInt(pcm.size * 2)
    return ByteArrayOutputStream().apply { write(h.array()); write(data.array()) }.toByteArray()
  }
}
