package com.ipflix.app

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

// Ton-Uhr des Players mit einstellbarem Versatz. Das Bild richtet sich nach dieser Uhr:
// offsetUs > 0 zeigt die Bilder früher, der Ton kommt also im Verhältnis später (und umgekehrt).
// So lässt sich ausgleichen, was Fernseher/Soundbar zusätzlich verzögern.
@UnstableApi
class SyncSink(sink: AudioSink) : ForwardingAudioSink(sink) {
  @Volatile var offsetUs = 0L
  // Zeitachse der Bilder liegt um den "Stream-Offset" des Players verschoben; für die Messung merken
  @Volatile var streamOffsetUs = 0L
  override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) { streamOffsetUs = outputStreamOffsetUs; super.setOutputStreamOffsetUs(outputStreamOffsetUs) }
  // ohne Versatz, in der Zeitachse der Bilder (ohne Stream-Offset), für die Messung
  fun rawPositionUs(): Long = super.getCurrentPositionUs(false).let { if (it == AudioSink.CURRENT_POSITION_NOT_SET) it else it - streamOffsetUs }
  override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
    val p = super.getCurrentPositionUs(sourceEnded)
    return if (p == AudioSink.CURRENT_POSITION_NOT_SET) p else p + offsetUs
  }
}

// Misst bei jedem Bild, wie weit es neben dem Ton liegt (Bildzeit minus Tonzeit beim Anzeigen), in ms.
@UnstableApi
class SyncMeter(private val sink: () -> SyncSink?) : androidx.media3.exoplayer.video.VideoFrameMetadataListener {
  private val errs = ArrayList<Double>()
  @Volatile var last = ""
  @Volatile var active = false
  override fun onVideoFrameAboutToBeRendered(presentationTimeUs: Long, releaseTimeNs: Long, format: androidx.media3.common.Format, mediaFormat: android.media.MediaFormat?) {
    if (!active) { if (errs.isNotEmpty()) errs.clear(); return }
    val s = sink() ?: return
    val a = s.rawPositionUs()
    if (a == AudioSink.CURRENT_POSITION_NOT_SET || a <= 0) return
    val audioAtRelease = a + (releaseTimeNs - System.nanoTime()) / 1000
    errs.add((presentationTimeUs - audioAtRelease) / 1000.0)
    if (errs.size >= 75) {
      val sorted = errs.sorted()
      val mean = errs.average()
      last = "Gemessen im Player: Bild zu Ton %+.1f ms (Median %+.1f, Schwankung %.1f … %.1f ms, %d Bilder)".format(
        mean, sorted[sorted.size / 2], sorted[sorted.size / 20], sorted[sorted.size * 19 / 20], errs.size)
      Log.i("IPFLIX-SYNC", last)
      errs.clear()
    }
  }
}
