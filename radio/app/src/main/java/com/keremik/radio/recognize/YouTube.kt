package com.keremik.radio.recognize

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.keremik.radio.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.net.URLEncoder

/** Öffnet zu einem Song direkt das passende YouTube-Video (erster Treffer), sonst die YouTube-Suche. */
object YouTube {
  private val videoId = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

  fun searchUrl(query: String) = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")

  /** Sucht die ID des ersten Videos zur Anfrage – ohne API-Schlüssel, über die normale Suchseite. */
  suspend fun findVideoId(query: String): String? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(8_000) {
      runCatching {
        val req = Request.Builder().url(searchUrl(query))
          .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0 Safari/537.36")
          .header("Accept-Language", "de-DE,de;q=0.9,tr;q=0.8,en;q=0.7")
          // Ohne dieses Cookie zeigt YouTube in der EU zuerst die Zustimmungsseite.
          .header("Cookie", "SOCS=CAI; CONSENT=YES+1")
          .build()
        Http.client.newCall(req).execute().use { r ->
          if (!r.isSuccessful) null else videoId.find(r.body.string())?.groupValues?.get(1)
        }
      }.getOrNull()
    }
  }

  suspend fun open(ctx: Context, query: String) {
    val id = findVideoId(query)
    val url = id?.let { "https://www.youtube.com/watch?v=$it" } ?: searchUrl(query)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      ctx.startActivity(Intent(intent).setPackage("com.google.android.youtube"))
    } catch (_: ActivityNotFoundException) {
      try { ctx.startActivity(intent) } catch (_: ActivityNotFoundException) { }
    }
  }
}
