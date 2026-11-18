plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
android {
  namespace = "com.ipflix.app"
  compileSdk = 34
  // Signatur-Schlüssel liegt nur verschlüsselt im Repo (ipflix-release.jks.enc). build-release.sh entschlüsselt ihn
  // mit dem privaten Passwort (Umgebungsvariable IPFLIX_KEY_PASS) nur für die Dauer des Baus.
  val keyPass: String? = System.getenv("IPFLIX_KEY_PASS")
  val keyFile = file("ipflix-private.jks")
  signingConfigs {
    create("release") {
      if (keyPass != null && keyFile.exists()) {
        storeFile = keyFile
        storePassword = keyPass
        keyAlias = "ipflix"
        keyPassword = keyPass
      }
    }
  }
  defaultConfig {
    applicationId = "com.ipflix.app"
    minSdk = 21
    targetSdk = 34
    versionCode = 16
    versionName = "3.2"
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
    getByName("debug") { isMinifyEnabled = false }
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
  from(rootProject.file("../app-ui/index.html"))
  from(rootProject.file("../app-ui/broadcast.json"))
  from(rootProject.file("../app-ui/icon.png"))
  into(layout.projectDirectory.dir("src/main/assets/web"))
}
tasks.register<Copy>("syncWebLib") {
  from(rootProject.file("../app-ui/lib"))
  into(layout.projectDirectory.dir("src/main/assets/web/lib"))
}
tasks.named("preBuild") { dependsOn("syncWeb", "syncWebLib") }
