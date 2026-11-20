package com.keremik.radio.data

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Http {
  const val USER_AGENT = "RadioDETR/1.0 (Android)"

  val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .callTimeout(40, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()
  }
}
