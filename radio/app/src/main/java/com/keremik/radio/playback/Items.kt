package com.keremik.radio.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.keremik.radio.data.Station

/** mediaId = "<liste>/<sender-uuid>", z. B. "de/9617a958-…". Die Liste bestimmt, wohin Weiter/Zurück springt. */
object Items {
  const val ROOT = "root"
  const val EXTRA_ICY = "icy"
  const val EXTRA_STATION = "station"

  fun mediaId(category: String, s: Station) = "$category/${s.id}"
  fun category(mediaId: String) = mediaId.substringBefore('/', "")
  fun stationId(mediaId: String) = mediaId.substringAfter('/', mediaId)

  fun countryLabel(s: Station) = when (s.country) { "DE" -> "Deutschland"; "TR" -> "Türkiye"; else -> "Radio" }

  fun baseMetadata(s: Station): MediaMetadata = MediaMetadata.Builder()
    .setTitle(s.name)
    .setDisplayTitle(s.name)
    .setStation(s.name)
    .setArtist(s.subtitle.ifBlank { countryLabel(s) })
    .setAlbumTitle(countryLabel(s))
    .setArtworkUri(s.favicon.takeIf { it.isNotBlank() }?.let(Uri::parse))
    .setIsPlayable(true)
    .setIsBrowsable(false)
    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
    .setExtras(Bundle().apply { putString(EXTRA_STATION, s.name) })
    .build()

  fun forStation(category: String, s: Station): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId(category, s))
    .setUri(s.url)
    .apply { if (s.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
    .setMediaMetadata(baseMetadata(s))
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(s.url)).build())
    .build()

  fun folder(id: String, title: String, type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS): MediaItem =
    MediaItem.Builder()
      .setMediaId(id)
      .setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(type).build()
      )
      .build()

  /** Metadaten mit dem laufenden Song aus dem Stream (ICY "Interpret - Titel"). */
  fun withSong(base: MediaMetadata, station: String, streamTitle: String, turkish: Boolean = false): MediaMetadata {
    val (artist, title) = splitStreamTitle(if (turkish) fixTurkish(streamTitle) else streamTitle)
    return base.buildUpon()
      .setTitle(title)
      .setDisplayTitle(title)
      .setArtist(artist ?: station)
      .setAlbumTitle(station)
      .setSubtitle(station)
      .setExtras(Bundle().apply { putString(EXTRA_STATION, station); putBoolean(EXTRA_ICY, artist != null) })
      .build()
  }

  /**
   * Viele türkische Sender schicken Titel in Windows-1254; als Latin-1 gelesen wird aus "ş" ein "þ" usw.
   * Diese Zeichen kommen in echten türkischen Titeln nicht vor, also lassen sie sich sicher zurückwandeln.
   */
  fun fixTurkish(t: String): String = t
    .replace('þ', 'ş').replace('Þ', 'Ş').replace('ý', 'ı').replace('Ý', 'İ').replace('ð', 'ğ').replace('Ð', 'Ğ')

  /** "Tarkan - Şımarık" → ("Tarkan", "Şımarık"). Ohne Trenner: (null, ganzer Text). */
  fun splitStreamTitle(t: String): Pair<String?, String> {
    val clean = t.trim().trim('\'', '"').replace(Regex("\\s+"), " ")
    for (sep in listOf(" - ", " – ", " — ", " | ", " / ")) {
      val i = clean.indexOf(sep)
      if (i > 0 && i < clean.length - sep.length) {
        return clean.substring(0, i).trim() to clean.substring(i + sep.length).trim()
      }
    }
    return null to clean
  }

  /** Viele Sender schicken statt eines Songs nur Werbung oder den eigenen Namen – das ignorieren wir. */
  fun isUsefulStreamTitle(t: String, station: String): Boolean {
    val c = t.trim()
    if (c.length < 3) return false
    val low = c.lowercase()
    if (low == station.lowercase()) return false
    val junk = listOf("http", "www.", "werbung", "reklam", "advert", "commercial", "jingle", "unknown", "live stream")
    return junk.none { low.contains(it) }
  }
}
