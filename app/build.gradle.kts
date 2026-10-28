plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun env(name: String): String = providers.environmentVariable(name).orNull.orEmpty()

android {
    namespace = "app.mylauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.mylauncher"
        // Fire OS 6 (Fire TV Stick 4K) is Android 7.1.2.
        minSdk = 25
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"

        // Nuvio account backend. Kept out of the public repo: CI reads them from secrets, local
        // builds from the environment. Empty values just hide the Nuvio row.
        buildConfigField("String", "NUVIO_SUPABASE_URL", "\"${env("NUVIO_SUPABASE_URL")}\"")
        buildConfigField("String", "NUVIO_SUPABASE_ANON_KEY", "\"${env("NUVIO_SUPABASE_ANON_KEY")}\"")
        // Italian titles and plots; without it the texts stay in English (Cinemeta).
        buildConfigField("String", "TMDB_API_KEY", "\"${env("TMDB_API_KEY")}\"")
    }

    signingConfigs {
        create("upload") {
            // Populated from environment variables in CI; absent locally, so release falls back
            // to the debug key below.
            val storePath = providers.environmentVariable("KEYSTORE_PATH").orNull
            if (storePath != null) {
                storeFile = file(storePath)
                // keytool on JDK 9+ writes PKCS12 whatever the extension.
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (providers.environmentVariable("KEYSTORE_PATH").orNull != null) {
                    signingConfigs.getByName("upload")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.tv.material)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
