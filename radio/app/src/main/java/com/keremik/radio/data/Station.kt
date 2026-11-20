package com.keremik.radio.data

import org.json.JSONObject

/** Ein Radiosender. [country] ist "DE" oder "TR". */
data class Station(
  val id: String,
  val name: String,
  val url: String,
  val favicon: String = "",
  val country: String = "",
  val tags: String = "",
  val codec: String = "",
  val bitrate: Int = 0,
  val hls: Boolean = false,
) {
  /** Kurze Beschreibung unter dem Namen, z. B. "Pop · Rock · MP3 128 kbit/s". */
  val subtitle: String
    get() {
      val t = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() && it.length <= 18 }
        .take(3).joinToString(" · ") { it.replaceFirstChar(Char::uppercase) }
      val q = buildString {
        if (codec.isNotBlank() && !codec.equals("unknown", true)) append(codec.uppercase())
        if (bitrate > 0) { if (isNotEmpty()) append(' '); append("$bitrate kbit/s") }
      }
      return listOf(t, q).filter { it.isNotEmpty() }.joinToString(" · ")
    }

  fun toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("url", url).put("favicon", favicon)
    .put("country", country).put("tags", tags).put("codec", codec).put("bitrate", bitrate).put("hls", hls)

  companion object {
    fun fromJson(o: JSONObject) = Station(
      id = o.getString("id"),
      name = o.getString("name"),
      url = o.getString("url"),
      favicon = o.optString("favicon"),
      country = o.optString("country"),
      tags = o.optString("tags"),
      codec = o.optString("codec"),
      bitrate = o.optInt("bitrate"),
      hls = o.optBoolean("hls"),
    )

    /** Ein Eintrag der radio-browser.info-API. */
    fun fromRadioBrowser(o: JSONObject, country: String): Station? {
      val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
      val name = o.optString("name").trim().replace(Regex("\\s+"), " ")
      val id = o.optString("stationuuid")
      if (url.isEmpty() || name.isEmpty() || id.isEmpty()) return null
      if (!url.startsWith("http")) return null
      return Station(
        id = id,
        name = name,
        url = url,
        favicon = o.optString("favicon").trim().takeIf { it.startsWith("http") } ?: "",
        country = country,
        tags = o.optString("tags"),
        codec = o.optString("codec"),
        bitrate = o.optInt("bitrate"),
        hls = o.optInt("hls") == 1 || url.contains(".m3u8", ignoreCase = true),
      )
    }
  }
}
