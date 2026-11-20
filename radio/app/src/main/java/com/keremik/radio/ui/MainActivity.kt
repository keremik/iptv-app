package com.keremik.radio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.keremik.radio.R

class MainActivity : ComponentActivity() {
  private val vm: RadioViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    // Immer dunkles Design: helle Symbole in Status- und Navigationsleiste.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
    )
    super.onCreate(savedInstanceState)
    setContent { RadioTheme { App(vm) } }
  }
}

private const val TAB_FAV = 0
private const val TAB_SONGS = 1
private const val TAB_RADIOS = 2
private const val TAB_CATS = 3
private const val TAB_MORE = 4

@Composable
internal fun App(vm: RadioViewModel) {
  var tab by rememberSaveable { mutableIntStateOf(TAB_FAV) }
  /** Unterseite im aktuellen Tab: "l-<id>" (eigene Liste) oder "g-<key>" (Kategorie). */
  var detail by rememberSaveable { mutableStateOf<String?>(null) }
  var sheet by remember { mutableStateOf(false) }
  val ctx = LocalContext.current

  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.recognize(micAllowed = granted) }
  val startRecognize = {
    val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (vm.now.isPlaying || hasMic) vm.recognize(micAllowed = hasMic) else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
  }

  BackHandler(enabled = detail != null) { detail = null }

  val nav = listOf(
    NavItem(R.string.tab_fav, Icons.Rounded.FavoriteBorder, Icons.Rounded.Favorite),
    NavItem(R.string.tab_songs, Icons.Rounded.LibraryMusic),
    NavItem(R.string.tab_radios, Icons.Rounded.Radio),
    NavItem(R.string.tab_categories, Icons.Rounded.Interests, Icons.Rounded.Category),
    NavItem(R.string.tab_more, Icons.Rounded.MoreHoriz),
  )

  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.weight(1f).fillMaxWidth().imePadding(), contentAlignment = Alignment.TopCenter) {
      val d = detail
      when {
        d != null && d.startsWith("l-") -> ListDetailScreen(vm, d.removePrefix("l-")) { detail = null }
        d != null && d.startsWith("g-") -> GenreDetailScreen(vm, d.removePrefix("g-")) { detail = null }
        tab == TAB_FAV -> FavoritesScreen(vm) { detail = "l-$it" }
        tab == TAB_SONGS -> SongsScreen(vm, onRecognize = startRecognize)
        tab == TAB_RADIOS -> RadiosScreen(vm)
        tab == TAB_CATS -> CategoriesScreen { detail = "g-$it" }
        else -> MoreScreen()
      }
    }
    MiniPlayer(vm) { sheet = true }
    BottomNav(nav, tab) { tab = it; detail = null }
  }

  if (sheet) PlayerSheet(vm, onDismiss = { sheet = false }, onRecognize = { sheet = false; tab = TAB_SONGS; detail = null; startRecognize() })

  vm.addToListFor?.let { s ->
    AddToListDialog(s, onDismiss = { vm.addToListFor = null }) { name ->
      Toast.makeText(ctx, ctx.getString(R.string.added_to_list, name), Toast.LENGTH_SHORT).show()
      vm.addToListFor = null
    }
  }
}
