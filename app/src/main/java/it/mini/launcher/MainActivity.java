plugins {
    id 'com.android.application'
}

android {
    namespace 'it.mini.launcher'
    compileSdk 34

    defaultConfig {
        applicationId 'it.mini.launcher'
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName '1.0'
    }

    // Firma fissa per l'APK di prova: così ogni nuova versione si installa sopra la precedente
    // senza cancellare i dati (ordine e nomi delle app scelti nel launcher).
    signingConfigs {
        debug {
            storeFile file('debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
