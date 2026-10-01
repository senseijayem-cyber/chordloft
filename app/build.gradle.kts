plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rexven.chordloft"
    compileSdk = 35

    defaultConfig {
        // The application ID is permanent once the app is on Google Play.
        applicationId = "com.rexven.chordloft"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("CHORDLOFT_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("CHORDLOFT_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHORDLOFT_KEY_ALIAS")
                keyPassword = System.getenv("CHORDLOFT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("CHORDLOFT_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.webkit:webkit:1.12.1")
}

// Bundle the app's fonts so it looks right offline. They are downloaded once at
// build time from the Google Fonts repository (SIL Open Font License).
// If a download fails, the build still succeeds and the app uses system fonts.
val fontFiles = mapOf(
    "YoungSerif-Regular.ttf" to "https://github.com/google/fonts/raw/main/ofl/youngserif/YoungSerif-Regular.ttf",
    "AtkinsonHyperlegible-Regular.ttf" to "https://github.com/google/fonts/raw/main/ofl/atkinsonhyperlegible/AtkinsonHyperlegible-Regular.ttf",
    "AtkinsonHyperlegible-Bold.ttf" to "https://github.com/google/fonts/raw/main/ofl/atkinsonhyperlegible/AtkinsonHyperlegible-Bold.ttf",
    "DMMono-Regular.ttf" to "https://github.com/google/fonts/raw/main/ofl/dmmono/DMMono-Regular.ttf",
    "DMMono-Medium.ttf" to "https://github.com/google/fonts/raw/main/ofl/dmmono/DMMono-Medium.ttf",
)

val fetchFonts by tasks.registering {
    val dir = layout.projectDirectory.dir("src/main/assets/fonts").asFile
    doLast {
        dir.mkdirs()
        fontFiles.forEach { (name, url) ->
            val out = File(dir, name)
            if (out.exists() && out.length() > 0) return@forEach
            try {
                java.net.URI(url).toURL().openStream().use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                logger.lifecycle("Downloaded font $name")
            } catch (e: Exception) {
                out.delete()
                logger.warn("Could not download $name, so the app will use a system font instead (${e.message})")
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchFonts) }
