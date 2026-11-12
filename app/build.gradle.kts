plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.svegliami"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.svegliami"
        minSdk = 26
        targetSdk = 36
        // Versione delle release con tag (vX.Y), quella che compila F-Droid.
        // Va aumentata a mano prima di ogni nuovo tag.
        versionCode = 1
        versionName = "1.0"
        // In GitHub Actions il numero di versione è il numero progressivo della build, così
        // ogni AAB per Google Play ha un versionCode più alto del precedente.
        System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let {
            versionCode = it
            versionName = "1.0.$it"
        }
    }

    // Chiave di firma fissa (da variabili d'ambiente, in CI dai segreti del repository).
    // Senza una chiave fissa ogni build avrebbe una firma diversa e Android
    // rifiuterebbe l'aggiornamento ("pacchetto in conflitto").
    val signingKeystore = System.getenv("SIGNING_KEYSTORE")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    signingConfigs {
        if (signingKeystore != null && signingPassword != null) {
            create("fixed") {
                storeFile = file(signingKeystore)
                storePassword = signingPassword
                keyAlias = "svegliami"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("fixed")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            // Per Google Play la stessa chiave fa da "chiave di caricamento":
            // la firma finale la applica Google (Play App Signing).
            signingConfigs.findByName("fixed")?.let { signingConfig = it }
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
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
