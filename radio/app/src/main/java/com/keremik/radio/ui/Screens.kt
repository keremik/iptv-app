package com.keremik.radio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keremik.radio.BuildConfig
import com.keremik.radio.R
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.Genres
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Station

/** Zweite Zeile einer Senderkarte: beim laufenden Sender der Status/Song, sonst optional die Musikrichtung. */
@Composable
fun stationSubtitle(s: Station, now: NowPlaying, showTags: Boolean): String? {
  if (now.station?.id == s.id) return statusLine(now)
  return if (showTags) s.subtitle else null
}

@Composable
fun statusLine(n: NowPlaying): String = when {
  n.error -> stringResource(R.string.playback_error)
  n.buffering -> stringResource(R.string.buffering)
  !n.isPlaying -> stringResource(R.string.paused)
  n.songTitle != null -> listOfNotNull(n.songArtist, n.songTitle).joinToString(" – ")
  else -> stringResource(R.string.playing)
}

/** Senderkarten einer Liste in eine LazyColumn. [category] bestimmt, wohin Weiter/Zurück im Auto springt. */
fun LazyListScope.stationItems(
  stations: List<Station>, category: String, vm: RadioViewModel, favIds: Set<String>, showTags: Boolean,
  trailing: (@Composable androidx.compose.foundation.layout.RowScope.(Int, Station) -> Unit)? = null,
) {
  itemsIndexed(stations, key = { _, s -> category + s.id }) { i, s ->
    val now = vm.now
    val current = now.station?.id == s.id
    StationCard(
      station = s, current = current, playing = current && now.isPlaying, favorite = s.id in favIds,
      subtitle = stationSubtitle(s, now, showTags),
      onClick = { vm.play(category, stations, s) },
      onLongClick = { vm.addToListFor = s },
      onFavorite = { vm.toggleFavorite(s) },
      trailing = trailing?.let { t -> { t(i, s) } },
    )
  }
}

private val listPadding = PaddingValues(bottom = 24.dp)

// ===================================================================== Favoriten

@Composable
fun FavoritesScreen(vm: RadioViewModel, onOpenList: (String) -> Unit) {
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  val lists by Prefs.lists.collectAsStateWithLifecycle()
  var edit by rememberSaveable { mutableStateOf(false) }
  var newList by remember { mutableStateOf(false) }
  var renameId by remember { mutableStateOf<String?>(null) }
  var deleteId by remember { mutableStateOf<String?>(null) }
  val favIds = favs.mapTo(HashSet()) { it.id }

  LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, horizontalAlignment = Alignment.CenterHorizontally) {
    item {
      PageHeader(stringResource(R.string.tab_fav)) {
        IconButton(onClick = { newList = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.new_list), tint = Ui.text, modifier = Modifier.size(34.dp)) }
        IconButton(onClick = { edit = !edit }) {
          Icon(if (edit) Icons.Rounded.Check else Icons.Rounded.Edit, stringResource(if (edit) R.string.done else R.string.edit),
            tint = if (edit) Ui.accent else Ui.text, modifier = Modifier.size(30.dp))
        }
      }
    }
    item { SectionTitle(stringResource(R.string.my_lists)) }
    if (lists.isEmpty()) item { HintText(stringResource(R.string.my_lists_empty)) }
    items(lists, key = { "list-" + it.id }) { l ->
      Row(
        Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(30.dp))
          .background(Ui.card).clickable { onOpenList(l.id) }.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Ui.cardHigh), contentAlignment = Alignment.Center) {
          Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Ui.accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
          Text(l.name, style = MaterialTheme.typography.titleLarge, color = Ui.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(stringResource(R.string.stations_count, l.stations.size), color = Ui.muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (edit) {
          IconButton(onClick = { renameId = l.id }) { Icon(Icons.Rounded.Edit, stringResource(R.string.rename), tint = Ui.text) }
          IconButton(onClick = { deleteId = l.id }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = Ui.heart) }
        } else Icon(Icons.Rounded.ChevronRight, null, tint = Ui.muted, modifier = Modifier.padding(end = 8.dp))
      }
    }
    item { SectionTitle(stringResource(R.string.fav_radios)) }
    if (favs.isEmpty()) item { HintText(stringResource(R.string.fav_empty_text)) }
    stationItems(favs, Catalog.FAV, vm, favIds, showTags = false, trailing = if (!edit) null else { i, s ->
      EditButtons(i, favs.size, onUp = { Prefs.moveFavorite(i, i - 1) }, onDown = { Prefs.moveFavorite(i, i + 1) },
        onDelete = { Prefs.removeFavorite(s.id) })
    })
  }

  if (newList) NameDialog(stringResource(R.string.new_list), "", stringResource(R.string.create), onDismiss = { newList = false }) {
    Prefs.createList(it); newList = false
  }
  renameId?.let { id ->
    NameDialog(stringResource(R.string.rename), Prefs.list(id)?.name.orEmpty(), stringResource(R.string.save), onDismiss = { renameId = null }) {
      Prefs.renameList(id, it); renameId = null
    }
  }
  deleteId?.let { id ->
    AlertDialog(
      onDismissRequest = { deleteId = null },
      title = { Text(stringResource(R.string.delete_list_q, Prefs.list(id)?.name.orEmpty())) },
      confirmButton = { TextButton(onClick = { Prefs.deleteList(id); deleteId = null }) { Text(stringResource(R.string.delete), color = Ui.heart) } },
      dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun EditButtons(i: Int, count: Int, onUp: () -> Unit, onDown: () -> Unit, onDelete: () -> Unit) {
  IconButton(onClick = onUp, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up), tint = if (i > 0) Ui.text else Ui.line) }
  IconButton(onClick = onDown, enabled = i < count - 1) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down), tint = if (i < count - 1) Ui.text else Ui.line) }
  IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete), tint = Ui.heart) }
}

/** Inhalt einer eigenen Liste. */
@Composable
fun ListDetailScreen(vm: RadioViewModel, listId: String, onBack: () -> Unit) {
  val lists by Prefs.lists.collectAsStateWithLifecycle()
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  val l = lists.firstOrNull { it.id == listId }
  var edit by rememberSaveable { mutableStateOf(false) }
  if (l == null) { onBack(); return }
  val cat = Catalog.LIST_PREFIX + l.id
  LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, horizontalAlignment = Alignment.CenterHorizontally) {
    item {
      PageHeader(l.name, stringResource(R.string.stations_count, l.stations.size), onBack = onBack) {
        if (l.stations.isNotEmpty()) IconButton(onClick = { edit = !edit }) {
          Icon(if (edit) Icons.Rounded.Check else Icons.Rounded.Edit, stringResource(if (edit) R.string.done else R.string.edit), tint = if (edit) Ui.accent else Ui.text)
        }
      }
    }
    if (l.stations.isEmpty()) item { HintText(stringResource(R.string.list_empty)) }
    stationItems(l.stations, cat, vm, favs.mapTo(HashSet()) { it.id }, showTags = false, trailing = if (!edit) null else { i, s ->
      EditButtons(i, l.stations.size, onUp = { Prefs.moveInList(l.id, i, i - 1) }, onDown = { Prefs.moveInList(l.id, i, i + 1) },
        onDelete = { Prefs.removeFromList(l.id, s.id) })
    })
  }
}

// ===================================================================== Radios (Deutschland / Türkei)

@Composable
fun RadiosScreen(vm: RadioViewModel) {
  var country by rememberSaveable { mutableStateOf(Catalog.DE) }
  val state by Catalog.state(country).collectAsStateWithLifecycle()
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  val list = remember(state.stations, query) {
    val q = Catalog.norm(query)
    if (q.isEmpty()) state.stations else state.stations.filter { Catalog.norm(it.name + " " + it.tags).contains(q) }
  }
  val focus = LocalFocusManager.current

  PullToRefreshBox(isRefreshing = state.loading && state.stations.isNotEmpty(), onRefresh = { Catalog.refresh(country) }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, horizontalAlignment = Alignment.CenterHorizontally) {
      item { PageHeader(stringResource(R.string.tab_radios), stringResource(R.string.stations_count, state.stations.size)) }
      item {
        Row(Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          CountryChip("🇩🇪", stringResource(R.string.tab_de), country == Catalog.DE, Modifier.weight(1f)) { country = Catalog.DE; focus.clearFocus() }
          CountryChip("🇹🇷", stringResource(R.string.tab_tr), country == Catalog.TR, Modifier.weight(1f)) { country = Catalog.TR; focus.clearFocus() }
        }
      }
      item { SearchField(query) { query = it } }
      if (state.error && state.stations.isNotEmpty()) item {
        Banner(stringResource(if (state.fallback) R.string.offline_list else R.string.load_error)) { Catalog.refresh(country) }
      }
      when {
        state.loading && state.stations.isEmpty() -> item {
          Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Ui.accent); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.loading), color = Ui.muted)
          }
        }
        state.stations.isEmpty() -> item {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HintText(stringResource(R.string.load_error_empty))
            TextButton(onClick = { Catalog.refresh(country) }) { Text(stringResource(R.string.retry), color = Ui.accent) }
          }
        }
        list.isEmpty() -> item { HintText(stringResource(R.string.no_results)) }
      }
      // Mit Suchbegriff schaltet Weiter/Zurück im Auto durch die Treffer.
      stationItems(list, country, vm, favs.mapTo(HashSet()) { it.id }, showTags = true)
    }
  }
}

@Composable
private fun CountryChip(flag: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  Row(
    modifier.clip(CircleShape).background(if (selected) Color.White else Ui.card).clickable(onClick = onClick).padding(vertical = 14.dp),
    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(flag, fontSize = 20.sp); Spacer(Modifier.width(8.dp))
    Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (selected) Color(0xFF052520) else Ui.text)
  }
}

@Composable
private fun Banner(text: String, onRetry: () -> Unit) {
  Row(
    Modifier.widthIn(max = MaxWidth).fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
      .background(Ui.cardHigh).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.CloudOff, null, tint = Ui.muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Ui.text)
    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = Ui.accent) }
  }
}

// ===================================================================== Kategorien

private val genreIcons: Map<String, Pair<ImageVector, Color>> = mapOf(
  "turkpop" to (Icons.Rounded.Star to Color(0xFFE53935)),
  "pop" to (Icons.Rounded.MusicNote to Color(0xFFEC407A)),
  "arabesk" to (Icons.Rounded.TheaterComedy to Color(0xFFAB47BC)),
  "turku" to (Icons.Rounded.MicExternalOn to Color(0xFFFF8F00)),
  "sanat" to (Icons.Rounded.LibraryMusic to Color(0xFF8D6E63)),
  "slow" to (Icons.Rounded.Favorite to Color(0xFFF06292)),
  "rock" to (Icons.Rounded.Album to Color(0xFF78909C)),
  "hiphop" to (Icons.Rounded.Headphones to Color(0xFFFFCA28)),
  "dance" to (Icons.Rounded.Nightlife to Color(0xFF26C6DA)),
  "schlager" to (Icons.Rounded.Celebration to Color(0xFF66BB6A)),
  "oldies" to (Icons.Rounded.History to Color(0xFF5C6BC0)),
  "news" to (Icons.Rounded.Newspaper to Color(0xFF29B6F6)),
  "jazz" to (Icons.Rounded.Piano to Color(0xFFBCAAA4)),
)

@Composable
fun CategoriesScreen(onOpen: (String) -> Unit) {
  val de by Catalog.state(Catalog.DE).collectAsStateWithLifecycle()
  val tr by Catalog.state(Catalog.TR).collectAsStateWithLifecycle()
  val counts = remember(de.stations, tr.stations) {
    Genres.all.associate { it.key to Genres.stations(it, de.stations, tr.stations).size }
  }
  LazyVerticalGrid(
    columns = GridCells.Adaptive(160.dp), modifier = Modifier.fillMaxSize().widthIn(max = MaxWidth),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item(span = { GridItemSpan(maxLineSpan) }) {
      Box(Modifier.offset(x = (-18).dp)) { PageHeader(stringResource(R.string.tab_categories), stringResource(R.string.both_countries)) }
    }
    items(Genres.all, key = { it.key }) { g ->
      val (icon, color) = genreIcons[g.key] ?: (Icons.Rounded.GraphicEq to Ui.accent)
      Column(
        Modifier.clip(RoundedCornerShape(28.dp)).background(Ui.card).clickable { onOpen(g.key) }.padding(18.dp).height(112.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
          Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
        }
        Column {
          Text(stringResource(g.label), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Ui.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(stringResource(R.string.stations_in_cat, counts[g.key] ?: 0), style = MaterialTheme.typography.bodySmall, color = Ui.muted)
        }
      }
    }
  }
}

@Composable
fun GenreDetailScreen(vm: RadioViewModel, key: String, onBack: () -> Unit) {
  val g = Genres.byKey(key) ?: run { onBack(); return }
  val de by Catalog.state(Catalog.DE).collectAsStateWithLifecycle()
  val tr by Catalog.state(Catalog.TR).collectAsStateWithLifecycle()
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  val list = remember(de.stations, tr.stations) { Genres.stations(g, de.stations, tr.stations) }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, horizontalAlignment = Alignment.CenterHorizontally) {
    item { PageHeader(stringResource(g.label), stringResource(R.string.stations_count, list.size), onBack = onBack) }
    if (list.isEmpty()) item { HintText(stringResource(R.string.no_results)) }
    stationItems(list, Catalog.GENRE_PREFIX + g.key, vm, favs.mapTo(HashSet()) { it.id }, showTags = true)
  }
}

// ===================================================================== Mehr

@Composable
fun MoreScreen() {
  var auto by remember { mutableStateOf(Prefs.autoYoutube) }
  var token by remember { mutableStateOf(Prefs.auddToken) }
  var tokenDialog by remember { mutableStateOf(false) }
  var refreshed by remember { mutableStateOf(false) }
  LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, horizontalAlignment = Alignment.CenterHorizontally) {
    item { PageHeader(stringResource(R.string.tab_more)) }
    item { SectionTitle(stringResource(R.string.more_recognition)) }
    item {
      DarkCard {
        Column {
          SettingRow(Icons.Rounded.SmartDisplay, stringResource(R.string.auto_youtube), null, onClick = { auto = !auto; Prefs.autoYoutube = auto }) {
            Switch(checked = auto, onCheckedChange = { auto = it; Prefs.autoYoutube = it },
              colors = SwitchDefaults.colors(checkedTrackColor = Ui.accent, checkedThumbColor = Color.White))
          }
          HorizontalDivider(Modifier.padding(start = 64.dp, end = 20.dp), color = Ui.line)
          SettingRow(Icons.Rounded.Key, stringResource(R.string.audd_token_label),
            stringResource(if (token.isBlank()) R.string.audd_token_none else R.string.audd_token_set), onClick = { tokenDialog = true })
        }
      }
    }
    item { SectionTitle(stringResource(R.string.more_stations)) }
    item {
      DarkCard {
        SettingRow(Icons.Rounded.Refresh, stringResource(R.string.refresh_lists),
          stringResource(if (refreshed) R.string.loading else R.string.refresh_lists_sub),
          onClick = { Catalog.COUNTRIES.forEach { Catalog.refresh(it) }; refreshed = true })
      }
    }
    item { SectionTitle(stringResource(R.string.car_title)) }
    item { DarkCard { SettingRow(Icons.Rounded.DirectionsCar, stringResource(R.string.car_hint), null, onClick = null, multiline = true) } }
    item { SectionTitle(stringResource(R.string.more_about)) }
    item {
      DarkCard {
        SettingRow(Icons.Rounded.Info, stringResource(R.string.app_name) + " · " + stringResource(R.string.version, BuildConfig.VERSION_NAME),
          stringResource(R.string.about_text), onClick = null, multiline = true)
      }
    }
  }
  if (tokenDialog) {
    var t by remember { mutableStateOf(token) }
    AlertDialog(
      onDismissRequest = { tokenDialog = false },
      title = { Text(stringResource(R.string.audd_token_label)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(value = t, onValueChange = { t = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
          Text(stringResource(R.string.audd_token_help), style = MaterialTheme.typography.bodySmall, color = Ui.muted)
        }
      },
      confirmButton = { TextButton(onClick = { Prefs.auddToken = t; token = t.trim(); tokenDialog = false }) { Text(stringResource(R.string.save)) } },
      dismissButton = { TextButton(onClick = { tokenDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun SettingRow(
  icon: ImageVector, title: String, sub: String?, onClick: (() -> Unit)?, multiline: Boolean = false,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = Ui.accent, modifier = Modifier.size(26.dp))
    Spacer(Modifier.width(18.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = if (multiline) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium, color = Ui.text)
      if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Ui.muted)
    }
    if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
  }
}

// ===================================================================== Dialoge

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var name by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.list_name)) }) },
    confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

/** Sender zu einer eigenen Liste hinzufügen (lange auf einen Sender drücken). */
@Composable
fun AddToListDialog(station: Station, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
  val lists by Prefs.lists.collectAsStateWithLifecycle()
  var create by remember { mutableStateOf(false) }
  if (create) {
    NameDialog(stringResource(R.string.new_list), "", stringResource(R.string.create), onDismiss = { create = false }) { name ->
      val l = Prefs.createList(name); Prefs.addToList(l.id, station); onAdded(l.name)
    }
    return
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.add_to_list)) },
    text = {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          StationLogo(station.name, station.favicon, 44.dp); Spacer(Modifier.width(12.dp))
          Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        lists.forEach { l ->
          val inList = l.stations.any { it.id == station.id }
          Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
              if (inList) Prefs.removeFromList(l.id, station.id) else { Prefs.addToList(l.id, station); onAdded(l.name) }
            }.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(if (inList) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Ui.accent)
            Spacer(Modifier.width(14.dp))
            Text(l.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
          }
        }
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { create = true }.padding(vertical = 12.dp, horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Rounded.Add, null, tint = Ui.accent); Spacer(Modifier.width(14.dp))
          Text(stringResource(R.string.new_list), style = MaterialTheme.typography.bodyLarge, color = Ui.accent)
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
  )
}
