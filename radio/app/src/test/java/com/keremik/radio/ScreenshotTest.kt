package com.keremik.radio

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.Fallback
import com.keremik.radio.data.Prefs
import com.keremik.radio.ui.App
import com.keremik.radio.ui.NowPlaying
import com.keremik.radio.ui.RadioTheme
import com.keremik.radio.ui.RadioViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Erzeugt Bildschirmfotos aller Bereiche nach build/screens (zur Kontrolle des Designs). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String) {
    rule.waitForIdle()
    val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
    val dir = File(System.getProperty("screens.dir") ?: "build/screens").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test
  fun screens() {
    val app = ApplicationProvider.getApplicationContext<RadioApp>()
    val tr = Fallback.stations(Catalog.TR)
    val de = Fallback.stations(Catalog.DE)
    listOf(tr[0], tr[2], de[0], tr[3]).forEach { if (!Prefs.isFavorite(it.id)) Prefs.toggleFavorite(it) }
    val vm = RadioViewModel(app, connect = false)
    rule.setContent { RadioTheme { App(vm) } }
    shot("1-favoriten-leer-listen")
    vm.now = NowPlaying(mediaId = "fav/" + tr[0].id, station = tr[0], isPlaying = true, songTitle = "Şımarık", songArtist = "Tarkan")
    shot("2-favoriten-spielt")
    rule.onNodeWithText("Erkannte Songs").performClick()
    shot("3-erkannte-songs")
    rule.onNodeWithText("Radios").performClick()
    Thread.sleep(1500)
    shot("4-radios")
    rule.onNodeWithText("Kategorien").performClick()
    shot("5-kategorien")
    rule.onNodeWithText("Mehr").performClick()
    shot("6-mehr")
    val l = Prefs.createList("Auto-Mix")
    Prefs.addToList(l.id, tr[1]); Prefs.addToList(l.id, de[2])
    rule.onNodeWithText("Favoriten").performClick()
    shot("7-favoriten-mit-liste")
    vm.addToListFor = de[4]
    shot("8-zu-liste-hinzufuegen")
    vm.addToListFor = null
    rule.onNodeWithText("Auto-Mix").performClick()
    shot("9-liste")
  }
}
