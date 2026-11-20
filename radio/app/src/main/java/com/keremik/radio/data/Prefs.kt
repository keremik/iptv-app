package com.keremik.radio.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Ein erkannter Song. */
data class Song(val artist: String, val title: String, val album: String = "", val artwork: String = "", val time: Long = 0) {
  val query: String get() = "$artist $title".trim()
  fun toJson(): JSONObject = JSONObject().put("artist", artist).put("title", title).put("album", album)
    .put("artwork", artwork).put("time", time)

  companion object {
    fun fromJson(o: JSONObject) = Song(o.optString("artist"), o.optString("title"), o.optString("album"),
      o.optString("artwork"), o.optLong("time"))
  }
}

/** Favoriten, Verlauf der Erkennung und Einstellungen. Alles bleibt auf dem Gerät. */
object Prefs {
  private lateinit var sp: SharedPreferences

  private val _favorites = MutableStateFlow<List<Station>>(emptyList())
  val favorites: StateFlow<List<Station>> = _favorites.asStateFlow()

  private val _history = MutableStateFlow<List<Song>>(emptyList())
  val history: StateFlow<List<Song>> = _history.asStateFlow()

  fun init(ctx: Context) {
    sp = ctx.getSharedPreferences("radio", Context.MODE_PRIVATE)
    _favorites.value = readList("favorites") { Station.fromJson(it) }
    _history.value = readList("history") { Song.fromJson(it) }
  }

  private fun <T> readList(key: String, parse: (JSONObject) -> T): List<T> = try {
    val arr = JSONArray(sp.getString(key, "[]"))
    (0 until arr.length()).mapNotNull { runCatching { parse(arr.getJSONObject(it)) }.getOrNull() }
  } catch (_: Exception) { emptyList() }

  fun isFavorite(id: String) = _favorites.value.any { it.id == id }

  fun toggleFavorite(s: Station) {
    val cur = _favorites.value
    val next = if (cur.any { it.id == s.id }) cur.filterNot { it.id == s.id } else cur + s
    _favorites.value = next
    sp.edit().putString("favorites", JSONArray(next.map { it.toJson() }).toString()).apply()
  }

  fun moveFavorite(from: Int, to: Int) {
    val list = _favorites.value.toMutableList()
    if (from !in list.indices || to !in list.indices) return
    list.add(to, list.removeAt(from))
    _favorites.value = list
    sp.edit().putString("favorites", JSONArray(list.map { it.toJson() }).toString()).apply()
  }

  fun addSong(song: Song) {
    val next = (listOf(song) + _history.value.filterNot { it.artist == song.artist && it.title == song.title }).take(50)
    _history.value = next
    sp.edit().putString("history", JSONArray(next.map { it.toJson() }).toString()).apply()
  }

  fun clearHistory() {
    _history.value = emptyList()
    sp.edit().remove("history").apply()
  }

  var auddToken: String
    get() = sp.getString("audd_token", "") ?: ""
    set(v) = sp.edit().putString("audd_token", v.trim()).apply()

  var autoYoutube: Boolean
    get() = sp.getBoolean("auto_youtube", true)
    set(v) = sp.edit().putBoolean("auto_youtube", v).apply()

  /** Zuletzt gespielter Sender (mediaId "liste/uuid"), damit die Wiedergabe im Auto per Play-Taste weitergeht. */
  var lastMediaId: String
    get() = sp.getString("last_media_id", "") ?: ""
    set(v) = sp.edit().putString("last_media_id", v).apply()
}
