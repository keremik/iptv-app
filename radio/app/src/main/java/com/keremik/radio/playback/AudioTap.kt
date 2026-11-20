package com.keremik.radio.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Hält die letzten Sekunden des laufenden Senders als Mono-PCM im Speicher.
 * So kann ein Song direkt aus dem Stream erkannt werden – ohne Mikrofon, auch wenn der Ton im Auto läuft.
 * Es wird nichts gespeichert oder gesendet, solange keine Erkennung angestoßen wird.
 */
@OptIn(UnstableApi::class)
object AudioTap : TeeAudioProcessor.AudioBufferSink {
  private const val SECONDS = 12
  private const val MAX_RATE = 48000

  private val ring = ShortArray(SECONDS * MAX_RATE)
  private var writePos = 0
  private var filled = 0
  private var rate = 0
  private var channels = 0
  private var encoding = C.ENCODING_INVALID
  private var lastWrite = 0L

  @Synchronized
  override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
    this.rate = sampleRateHz
    this.channels = channelCount
    this.encoding = encoding
    writePos = 0
    filled = 0
  }

  @Synchronized
  override fun handleBuffer(buffer: ByteBuffer) {
    if (encoding != C.ENCODING_PCM_16BIT || channels <= 0 || rate <= 0 || rate > MAX_RATE) return
    val b = buffer.order(ByteOrder.LITTLE_ENDIAN)
    val cap = SECONDS * rate
    while (b.remaining() >= 2 * channels) {
      var sum = 0
      for (c in 0 until channels) sum += b.short.toInt()
      ring[writePos] = (sum / channels).toShort()
      writePos = (writePos + 1) % cap
      if (filled < cap) filled++
    }
    lastWrite = SystemClock.elapsedRealtime()
  }

  /** Die letzten [seconds] Sekunden (Mono, 16 bit) und die Abtastrate, falls der Sender gerade läuft. */
  @Synchronized
  fun snapshot(seconds: Int): Pair<ShortArray, Int>? {
    if (rate <= 0 || SystemClock.elapsedRealtime() - lastWrite > 1500) return null
    val cap = SECONDS * rate
    val n = minOf(filled, seconds * rate)
    if (n < 6 * rate) return null
    val out = ShortArray(n)
    var p = (writePos - n + cap) % cap
    for (i in 0 until n) { out[i] = ring[p]; p = (p + 1) % cap }
    return out to rate
  }
}
