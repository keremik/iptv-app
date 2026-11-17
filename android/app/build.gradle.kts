plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.ipflix.app"
  compileSdk = 34
  signingConfigs {
    create("release") {
      storeFile = file("ipflix-release.jks")
      storePassword = "ipflixapp"
      keyAlias = "ipflix"
      keyPassword = "ipflixapp"
    }
  }
  defaultConfig {
    applicationId = "com.ipflix.app"
    minSdk = 21
    targetSdk = 34
    versionCode = 10
    versionName = "1.9"
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
    getByName("debug") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }
}
dependencies {
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.webkit:webkit:1.11.0")
  implementation("androidx.media3:media3-exoplayer:1.3.1")
  implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
  implementation("androidx.media3:media3-ui:1.3.1")
}

// Oberfläche und Daten aus dem Hauptprojekt vor jedem Bau in die App kopieren,
// damit App und Webseite immer denselben Stand haben.
tasks.register<Copy>("syncWeb") {
  from(rootProject.file("../index.html"))
  from(rootProject.file("../broadcast.json"))
  from(rootProject.file("../icon.png"))
  into(layout.projectDirectory.dir("src/main/assets/web"))
  // Allgemeine App: keine persoenliche Proxy-Adresse in der App-Version
  filesMatching("index.html") {
    filter { line -> line.replace("https://iptv-proxy.keremirmak02.workers.dev", "") }
  }
}
tasks.register<Copy>("syncWebLib") {
  from(rootProject.file("../lib"))
  into(layout.projectDirectory.dir("src/main/assets/web/lib"))
}
tasks.named("preBuild") { dependsOn("syncWeb", "syncWebLib") }
