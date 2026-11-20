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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keremik.radio.R
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.ListState
import com.keremik.radio.data.Prefs
import com.keremik.radio.data.Station
import com.keremik.radio.playback.Items

/** Liste eines Landes (Deutschland/Türkei) mit Suche und Ziehen zum Aktualisieren. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryScreen(category: String, title: String, vm: RadioViewModel) {
  val state: ListState by Catalog.state(category).collectAsStateWithLifecycle()
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  var query by rememberSaveable(category) { mutableStateOfString() }
  val list = remember(state.stations, query) {
    val q = Catalog.norm(query)
    if (q.isEmpty()) state.stations else state.stations.filter { Catalog.norm(it.name + " " + it.tags).contains(q) }
  }
  val subtitle = stringResource(R.string.stations_count, state.stations.size)

  StationPage(title = title, subtitle = subtitle) { padding ->
    PullToRefreshBox(
      isRefreshing = state.loading && state.stations.isNotEmpty(),
      onRefresh = { Catalog.refresh(category) },
      modifier = Modifier.fillMaxSize().padding(padding),
    ) {
      StationList(
        header = {
          SearchField(query) { query = it }
          if (state.error && state.stations.isNotEmpty()) {
            Banner(stringResource(if (state.fallback) R.string.offline_list else R.string.load_error)) { Catalog.refresh(category) }
          }
        },
        stations = list,
        category = category,
        favoriteIds = favs.mapTo(HashSet()) { it.id },
        vm = vm,
        empty = {
          when {
            state.loading && state.stations.isEmpty() -> Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator()
              Spacer(Modifier.height(16.dp))
              Text(stringResource(R.string.loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.stations.isEmpty() -> EmptyHint(stringResource(R.string.load_error_empty), null) {
              TextButton(onClick = { Catalog.refresh(category) }) { Text(stringResource(R.string.retry)) }
            }
            else -> EmptyHint(stringResource(R.string.no_results), null)
          }
        },
        // Mit Suchbegriff: Weiter/Zurück im Auto schaltet durch die Suchtreffer.
        playlist = list,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: RadioViewModel) {
  val favs by Prefs.favorites.collectAsStateWithLifecycle()
  StationPage(title = stringResource(R.string.tab_fav), subtitle = stringResource(R.string.stations_count, favs.size)) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      StationList(
        header = {},
        stations = favs,
        category = Catalog.FAV,
        favoriteIds = favs.mapTo(HashSet()) { it.id },
        vm = vm,
        empty = { EmptyHint(stringResource(R.string.fav_empty_text), stringResource(R.string.fav_empty_title)) },
        playlist = favs,
      )
    }
  }
}

/** Seite mit großem One-UI-Titel, der beim Scrollen zusammenklappt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationPage(
  title: String,
  subtitle: String?,
  actions: @Composable () -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    containerColor = Ui.page,
    contentWindowInsets = WindowInsets(0),
    topBar = {
      LargeTopAppBar(
        title = {
          Column {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null && scroll.state.collapsedFraction < 0.5f) {
              Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        },
        actions = { actions() },
        expandedHeight = 180.dp,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Ui.page, scrolledContainerColor = Ui.page),
        scrollBehavior = scroll,
      )
    },
    content = content,
  )
}

@Composable
private fun StationList(
  header: @Composable () -> Unit,
  stations: List<Station>,
  category: String,
  favoriteIds: Set<String>,
  vm: RadioViewModel,
  empty: @Composable () -> Unit,
  playlist: List<Station>,
) {
  val now = vm.now
  val focus = LocalFocusManager.current
  LazyColumn(
    state = rememberLazyListState(),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    item { Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { header() } }
    if (stations.isEmpty()) item { Box(Modifier.widthIn(max = 720.dp).fillMaxWidth()) { empty() } }
    itemsIndexed(stations, key = { _, s -> s.id }) { i, s ->
      val current = now.mediaId == Items.mediaId(category, s) || (now.station?.id == s.id)
      StationRow(
        station = s,
        shape = groupShape(i, stations.size),
        showDivider = i < stations.size - 1,
        current = current,
        playing = current && now.isPlaying,
        favorite = s.id in favoriteIds,
        onClick = { focus.clearFocus(); vm.play(category, playlist, s) },
        onFavorite = { vm.toggleFavorite(s) },
      )
    }
  }
}

@Composable
private fun StationRow(
  station: Station,
  shape: androidx.compose.ui.graphics.Shape,
  showDivider: Boolean,
  current: Boolean,
  playing: Boolean,
  favorite: Boolean,
  onClick: () -> Unit,
  onFavorite: () -> Unit,
) {
  Column(
    Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 16.dp)
      .clip(shape).background(Ui.card).clickable(onClick = onClick)
  ) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      StationLogo(station.name, station.favicon, 48.dp)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(
          station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
          color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
          fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
        )
        if (station.subtitle.isNotBlank()) {
          Text(
            station.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (current) Equalizer(playing, modifier = Modifier.padding(horizontal = 6.dp))
      IconButton(onClick = onFavorite) {
        Icon(
          if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
          contentDescription = stringResource(if (favorite) R.string.remove_fav else R.string.add_fav),
          tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (showDivider) HorizontalDivider(Modifier.padding(start = 78.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
  }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
  val focus = LocalFocusManager.current
  TextField(
    value = value,
    onValueChange = onChange,
    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
    placeholder = { Text(stringResource(R.string.search_hint)) },
    leadingIcon = { Icon(Icons.Rounded.Search, null) },
    trailingIcon = {
      if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
    },
    singleLine = true,
    shape = CircleShape,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
    colors = TextFieldDefaults.colors(
      focusedContainerColor = Ui.card, unfocusedContainerColor = Ui.card,
      focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
      unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    ),
  )
}

@Composable
private fun Banner(text: String, onRetry: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp).clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
  }
}

@Composable
fun EmptyHint(text: String, title: String?, action: @Composable () -> Unit = {}) {
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 56.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (title != null) {
      Icon(Icons.Rounded.StarOutline, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
      Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    action()
  }
}

private fun mutableStateOfString() = androidx.compose.runtime.mutableStateOf("")
