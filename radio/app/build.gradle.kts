plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.keremik.radio"
  compileSdk = 37

  // Signatur-Schlüssel liegt nur verschlüsselt im Repo (radio-release.jks.enc). build-release.sh entschlüsselt ihn
  // mit dem privaten Passwort (RADIO_KEY_PASS) nur für die Dauer des Baus.
  val keyPass: String? = System.getenv("RADIO_KEY_PASS")
  val keyFile = file("radio-private.jks")
  signingConfigs {
    create("release") {
      if (keyPass != null && keyFile.exists()) {
        storeFile = keyFile
        storePassword = keyPass
        keyAlias = "radio"
        keyPassword = keyPass
      }
    }
  }

  defaultConfig {
    applicationId = "com.keremik.radio"
    minSdk = 26
    targetSdk = 37
    versionCode = 2
    versionName = "1.1"
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
  }
  buildFeatures { compose = true; buildConfig = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions { unitTests.isIncludeAndroidResources = true }
  packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
  val media3 = "1.10.1"
  implementation(platform("androidx.compose:compose-bom:2026.09.00"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.12.4")
  implementation("androidx.core:core-ktx:1.18.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
  implementation("androidx.media3:media3-exoplayer:$media3")
  implementation("androidx.media3:media3-exoplayer-hls:$media3")
  implementation("androidx.media3:media3-session:$media3")
  implementation("com.squareup.okhttp3:okhttp:5.4.0")
  implementation("io.coil-kt.coil3:coil-compose:3.6.1")
  implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

  // Nur für Bildschirmfotos der Oberfläche auf der JVM (Robolectric), nicht in der App.
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.robolectric:robolectric:4.18.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
