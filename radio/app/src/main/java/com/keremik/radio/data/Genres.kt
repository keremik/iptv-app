package com.keremik.radio.data

import com.keremik.radio.R

/** Kategorie über beide Länder, erkannt an den Stichwörtern (tags) der Sender. */
data class Genre(val key: String, val label: Int, val keywords: List<String>)

object Genres {
  val all = listOf(
    Genre("turkpop", R.string.genre_turkpop, listOf("türkçe pop", "turkce pop", "turkish pop", "türk pop", "turk pop")),
    Genre("pop", R.string.genre_pop, listOf("pop", "hits", "charts", "top 40")),
    Genre("arabesk", R.string.genre_arabesk, listOf("arabesk", "arabesque", "fantezi")),
    Genre("turku", R.string.genre_turku, listOf("türkü", "turku", "halk", "türk halk", "anadolu")),
    Genre("sanat", R.string.genre_sanat, listOf("sanat", "fasıl", "fasil", "klasik türk", "türk sanat")),
    Genre("slow", R.string.genre_slow, listOf("slow", "romantic", "romantik", "love", "ballad")),
    Genre("rock", R.string.genre_rock, listOf("rock", "metal", "alternative", "indie")),
    Genre("hiphop", R.string.genre_hiphop, listOf("hip-hop", "hiphop", "hip hop", "rap", "rnb", "r&b", "urban")),
    Genre("dance", R.string.genre_dance, listOf("dance", "electronic", "edm", "house", "techno", "trance", "club")),
    Genre("schlager", R.string.genre_schlager, listOf("schlager", "volksmusik", "deutschpop", "neue deutsche")),
    Genre("oldies", R.string.genre_oldies, listOf("oldies", "70s", "80s", "90s", "80er", "90er", "classic hits", "retro", "nostalji")),
    Genre("news", R.string.genre_news, listOf("news", "talk", "haber", "info", "nachrichten", "spor", "sport")),
    Genre("jazz", R.string.genre_jazz, listOf("jazz", "classical", "klassik", "klasik", "blues", "soul", "lounge")),
  )

  fun byKey(key: String) = all.firstOrNull { it.key == key }

  fun matches(g: Genre, s: Station): Boolean {
    val tags = s.tags.lowercase()
    if (tags.isBlank()) return false
    return g.keywords.any { tags.contains(it) }
  }

  /** Sender beider Länder in der Kategorie, abwechselnd nach Beliebtheit (DE, TR, DE, …). */
  fun stations(g: Genre, de: List<Station>, tr: List<Station>): List<Station> {
    val a = de.filter { matches(g, it) }
    val b = tr.filter { matches(g, it) }
    val out = ArrayList<Station>(a.size + b.size)
    for (i in 0 until maxOf(a.size, b.size)) {
      b.getOrNull(i)?.let(out::add)
      a.getOrNull(i)?.let(out::add)
    }
    return out
  }
}
