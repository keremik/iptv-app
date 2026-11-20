package com.keremik.radio.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.keremik.radio.R
import com.keremik.radio.data.Catalog

class MainActivity : ComponentActivity() {
  private val vm: RadioViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent { RadioTheme { App(vm) } }
  }
}

private data class Tab(val label: Int, val emoji: String?, val icon: (@Composable (Boolean) -> Unit)?)

@Composable
private fun App(vm: RadioViewModel) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var sheet by remember { mutableStateOf(false) }
  val ctx = LocalContext.current

  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    vm.recognize(micAllowed = granted)
  }
  val startRecognize = {
    val hasMic = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    if (vm.now.isPlaying || hasMic) vm.recognize(micAllowed = hasMic) else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
  }

  val tabs = listOf(
    Tab(R.string.tab_de, "🇩🇪", null),
    Tab(R.string.tab_tr, "🇹🇷", null),
    Tab(R.string.tab_fav, null) { sel -> Icon(if (sel) Icons.Rounded.Star else Icons.Rounded.StarOutline, null) },
    Tab(R.string.tab_rec, null) { _ -> Icon(Icons.Rounded.GraphicEq, null) },
  )

  Scaffold(
    containerColor = Ui.page,
    contentWindowInsets = WindowInsets(0),
    bottomBar = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        MiniPlayer(vm) { sheet = true }
        NavigationBar(containerColor = Ui.page) {
          tabs.forEachIndexed { i, t ->
            NavigationBarItem(
              selected = tab == i,
              onClick = { tab = i },
              icon = { if (t.emoji != null) Text(t.emoji, fontSize = 22.sp) else t.icon?.invoke(tab == i) },
              label = { Text(stringResource(t.label), maxLines = 1) },
            )
          }
        }
      }
    },
  ) { padding ->
    Column(Modifier.padding(padding)) {
      when (tab) {
        0 -> CountryScreen(Catalog.DE, stringResource(R.string.tab_de), vm)
        1 -> CountryScreen(Catalog.TR, stringResource(R.string.tab_tr), vm)
        2 -> FavoritesScreen(vm)
        else -> RecognizeScreen(vm, onRecognize = startRecognize)
      }
    }
  }

  if (sheet) {
    PlayerSheet(vm, onDismiss = { sheet = false }, onRecognize = { sheet = false; tab = 3; startRecognize() })
  }
}
