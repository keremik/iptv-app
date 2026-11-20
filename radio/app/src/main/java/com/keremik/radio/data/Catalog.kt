package com.keremik.radio.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.text.Normalizer

data class ListState(
  val stations: List<Station> = emptyList(),
  val loading: Boolean = false,
  /** Laden aus dem Netz ist fehlgeschlagen (es wird ggf. der gespeicherte Stand gezeigt). */
  val error: Boolean = false,
  /** Es wird nur die eingebaute Notfall-Liste gezeigt. */
  val fallback: Boolean = false,
)

/**
 * Senderlisten für Deutschland und die Türkei aus dem freien Verzeichnis radio-browser.info.
 * Die Listen werden auf dem Gerät gespeichert; die App startet sofort mit dem letzten Stand und
 * aktualisiert im Hintergrund. Ohne Netz und ohne Speicherstand gibt es eine kleine eingebaute Liste.
 */
object Catalog {
  const val DE = "de"
  const val TR = "tr"
  const val FAV = "fav"
  val COUNTRIES = listOf(DE, TR)

  private lateinit var app: Context
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val states = COUNTRIES.associateWith { MutableStateFlow(ListState(loading = true)) }
  private val locks = COUNTRIES.associateWith { Mutex() }
  private val loaded = COUNTRIES.associateWith { MutableStateFlow(false) }

  private val servers = listOf("de1", "de2", "fi1", "nl1", "at1")

  fun init(ctx: Context) {
    app = ctx.applicationContext
    COUNTRIES.forEach { cat ->
      scope.launch {
        val cached = readCache(cat)
        if (cached.isNotEmpty()) states.getValue(cat).value = ListState(cached, loading = true)
        loaded.getValue(cat).value = cached.isNotEmpty()
        refreshNow(cat)
      }
    }
  }

  fun state(cat: String): StateFlow<ListState> = states.getValue(cat)

  fun refresh(cat: String) { scope.launch { refreshNow(cat) } }

  private suspend fun refreshNow(cat: String) = locks.getValue(cat).withLock {
    val st = states.getValue(cat)
    st.value = st.value.copy(loading = true, error = false)
    val fresh = runCatching { fetch(cat) }.getOrNull()
    if (!fresh.isNullOrEmpty()) {
      st.value = ListState(fresh)
      writeCache(cat, fresh)
    } else {
      val cur = st.value.stations
      st.value = if (cur.isNotEmpty()) st.value.copy(loading = false, error = true)
      else ListState(Fallback.stations(cat), loading = false, error = true, fallback = true)
    }
    loaded.getValue(cat).value = true
  }

  /** Senderliste einer Kategorie; wartet beim ersten Start, bis eine Liste da ist. */
  suspend fun stations(cat: String): List<Station> {
    if (cat == FAV) return Prefs.favorites.value
    val flow = loaded[cat] ?: return emptyList()
    flow.first { it }
    return states.getValue(cat).value.stations
  }

  fun find(id: String): Station? =
    Prefs.favorites.value.firstOrNull { it.id == id }
      ?: states.values.firstNotNullOfOrNull { s -> s.value.stations.firstOrNull { it.id == id } }

  /** Suche über beide Länder (für Sprachbefehle und die Suche in Android Auto). */
  suspend fun search(query: String): List<Station> {
    val q = norm(query)
    if (q.isEmpty()) return emptyList()
    val all = (Prefs.favorites.value + stations(DE) + stations(TR)).distinctBy { it.id }
    return all.filter { norm(it.name).contains(q) }.sortedBy { if (norm(it.name).startsWith(q)) 0 else 1 }
  }

  private suspend fun fetch(cat: String): List<Station> = withContext(Dispatchers.IO) {
    val country = cat.uppercase()
    var lastError: Exception? = null
    for (host in servers.shuffled() + "all") {
      try {
        val url = "https://$host.api.radio-browser.info/json/stations/search" +
          "?countrycode=$country&hidebroken=true&order=clickcount&reverse=true&limit=700"
        val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        Http.client.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
          val arr = JSONArray(resp.body.string())
          val seen = HashSet<String>()
          val out = ArrayList<Station>()
          for (i in 0 until arr.length()) {
            val s = Station.fromRadioBrowser(arr.getJSONObject(i), country) ?: continue
            if (seen.add(norm(s.name))) out += s
            if (out.size >= 450) break
          }
          if (out.isNotEmpty()) return@withContext out
        }
      } catch (e: Exception) {
        lastError = e
      }
    }
    throw lastError ?: java.io.IOException("keine Sender")
  }

  /** Vergleichsform eines Sendernamens: klein, ohne Akzente, Leer- und Sonderzeichen. */
  fun norm(s: String): String {
    val lower = s.lowercase()
      .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ç', 'c').replace('ö', 'o').replace('ü', 'u')
      .replace("ß", "ss")
    return Normalizer.normalize(lower, Normalizer.Form.NFD).filter { it.isLetterOrDigit() && it.code < 0x300 }
  }

  private fun cacheFile(cat: String) = File(app.filesDir, "stations_$cat.json")

  private fun readCache(cat: String): List<Station> = try {
    val f = cacheFile(cat)
    if (!f.exists()) emptyList() else {
      val arr = JSONArray(f.readText())
      (0 until arr.length()).map { Station.fromJson(arr.getJSONObject(it)) }
    }
  } catch (_: Exception) { emptyList() }

  private fun writeCache(cat: String, list: List<Station>) {
    runCatching {
      val tmp = File(app.filesDir, "stations_$cat.tmp")
      tmp.writeText(JSONArray(list.map { it.toJson() }).toString())
      tmp.renameTo(cacheFile(cat))
    }
  }
}
