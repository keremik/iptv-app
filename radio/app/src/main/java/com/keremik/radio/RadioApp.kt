package com.keremik.radio

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.keremik.radio.data.Catalog
import com.keremik.radio.data.Http
import com.keremik.radio.data.Prefs

class RadioApp : Application(), SingletonImageLoader.Factory {
  override fun onCreate() {
    super.onCreate()
    Prefs.init(this)
    Catalog.init(this)
  }

  override fun newImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
      .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
      .crossfade(true)
      .build()
}
