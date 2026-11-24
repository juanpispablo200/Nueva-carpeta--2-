import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Se aplican solo si google-services.json existe; sin él el proyecto compila igual
// (Firebase queda sin inicializar y Crashlytics no reporta nada).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

val keystoreProps = Properties()
val keystoreDriverFile = rootProject.file("keystore.driver.properties")
if (keystoreDriverFile.exists()) {
    keystoreDriverFile.inputStream().use { keystoreProps.load(it) }
}

// Abortar bundleRelease/assembleRelease si falta o está incompleto keystore.driver.properties
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any {
        it.project == project && (it.name == "bundleRelease" || it.name == "assembleRelease")
    }
    if (releaseRequested) {
        val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val missing = required.filter { keystoreProps.getProperty(it).isNullOrBlank() }
        if (!keystoreDriverFile.exists() || missing.isNotEmpty()) {
            throw GradleException(
                "FIRMA RELEASE (driver): falta keystore.driver.properties en la raíz del proyecto " +
                "o le faltan claves: ${if (keystoreDriverFile.exists()) missing else required}. " +
                "Copia keystore.driver.properties.example como keystore.driver.properties y complétalo."
            )
        }
        if (!file(keystoreProps.getProperty("storeFile")).exists()) {
            throw GradleException(
                "FIRMA RELEASE (driver): el archivo .jks indicado en storeFile no existe. " +
                "Genéralo con keytool (ver keystore.driver.properties.example)."
            )
        }
    }
}

android {
    namespace  = "com.jagoo.driver"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jagoo.driver"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 2
        versionName   = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        manifestPlaceholders["crashlyticsEnabled"] = "true"

        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("supabase.anon.key", "")}\"")
    }

    signingConfigs {
        if (keystoreDriverFile.exists()) {
            create("release") {
                storeFile     = file(keystoreProps.getProperty("storeFile", ""))
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias      = keystoreProps.getProperty("keyAlias", "")
                keyPassword   = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        debug {
            // Sin reportes de crash durante desarrollo
            manifestPlaceholders["crashlyticsEnabled"] = "false"
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreDriverFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)

    // Supabase
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)

    // Firebase — notificaciones push FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    // Google Maps — para ver posición del repartidor
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Ktor
    implementation(libs.ktor.okhttp)

    // Serialization
    implementation(libs.kotlin.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
