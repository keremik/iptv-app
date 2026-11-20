package com.keremik.radio.data

/** Kleine eingebaute Liste, falls beim allerersten Start kein Senderverzeichnis erreichbar ist. */
object Fallback {
  private fun de(id: String, name: String, url: String, tags: String) =
    Station("fb-de-$id", name, url, country = "DE", tags = tags, codec = "MP3")

  private fun tr(id: String, name: String, url: String, tags: String) =
    Station("fb-tr-$id", name, url, country = "TR", tags = tags, codec = "MP3")

  fun stations(cat: String): List<Station> = when (cat) {
    Catalog.DE -> listOf(
      de("1live", "1LIVE", "https://wdr-1live-live.icecastssl.wdr.de/wdr/1live/live/mp3/128/stream.mp3", "pop,charts"),
      de("wdr2", "WDR 2", "https://wdr-wdr2-rheinland.icecastssl.wdr.de/wdr/wdr2/rheinland/mp3/128/stream.mp3", "pop,news"),
      de("swr3", "SWR3", "https://liveradio.swr.de/sw282p3/swr3/play.mp3", "pop,rock"),
      de("bayern3", "Bayern 3", "https://dispatcher.rndfnk.com/br/br3/live/mp3/mid", "pop"),
      de("ndr2", "NDR 2", "https://icecast.ndr.de/ndr/ndr2/niedersachsen/mp3/128/stream.mp3", "pop"),
      de("hr3", "hr3", "https://dispatcher.rndfnk.com/hr/hr3/live/mp3/high", "pop"),
      de("fritz", "Fritz", "https://dispatcher.rndfnk.com/rbb/fritz/live/mp3/mid", "alternative"),
      de("antenne", "ANTENNE BAYERN", "https://stream.antenne.de/antenne", "pop,hits"),
      de("bigfm", "bigFM", "https://streams.bigfm.de/bigfm-deutschland-128-mp3", "hip-hop,dance"),
      de("dlf", "Deutschlandfunk", "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3", "news,kultur"),
    )
    Catalog.TR -> listOf(
      tr("powerturk", "Power Türk", "https://listen.powerapp.com.tr/powerturk/mpeg/icecast.audio", "türkçe pop"),
      tr("powerfm", "Power FM", "https://listen.powerapp.com.tr/powerfm/mpeg/icecast.audio", "pop,hits"),
      tr("superfm", "Süper FM", "https://playerservices.streamtheworld.com/api/livestream-redirect/SUPER_FM.mp3", "türkçe pop"),
      tr("joyturk", "Joy Türk", "https://playerservices.streamtheworld.com/api/livestream-redirect/JOY_TURK.mp3", "türkçe slow"),
      tr("joyfm", "Joy FM", "https://playerservices.streamtheworld.com/api/livestream-redirect/JOY_FM.mp3", "slow"),
      tr("metrofm", "Metro FM", "https://playerservices.streamtheworld.com/api/livestream-redirect/METRO_FM.mp3", "pop,hits"),
      tr("fenomen", "Radyo Fenomen", "https://listen.radyofenomen.com/fenomen/128/icecast.audio", "pop,hits"),
      tr("fenomenturk", "Fenomen Türk", "https://listen.radyofenomen.com/fenomenturk/128/icecast.audio", "türkçe pop"),
    )
    else -> emptyList()
  }
}
