import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Aplicar google-services solo si google-services.json está presente
// Se aplican solo si google-services.json existe; sin él el proyecto compila igual
// (Firebase queda sin inicializar y Crashlytics no reporta nada).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Leer claves desde local.properties — nunca hardcodear secretos
val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}

// Leer keystore.properties si existe (CI/CD y builds de release)
val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) {
    keystoreFile.inputStream().use { keystoreProps.load(it) }
}

// Abortar bundleRelease/assembleRelease si falta o está incompleto keystore.properties
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any {
        it.project == project && (it.name == "bundleRelease" || it.name == "assembleRelease")
    }
    if (releaseRequested) {
        val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val missing = required.filter { keystoreProps.getProperty(it).isNullOrBlank() }
        if (!keystoreFile.exists() || missing.isNotEmpty()) {
            throw GradleException(
                "FIRMA RELEASE: falta keystore.properties en la raíz del proyecto " +
                "o le faltan claves: ${if (keystoreFile.exists()) missing else required}. " +
                "Copia keystore.properties.example como keystore.properties y complétalo."
            )
        }
        if (!file(keystoreProps.getProperty("storeFile")).exists()) {
            throw GradleException(
                "FIRMA RELEASE: el archivo .jks indicado en storeFile no existe. " +
                "Genéralo con keytool (ver keystore.properties.example)."
            )
        }
    }
}

android {
    namespace  = "com.jagoo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jagoo.app"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 2
        versionName   = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Supabase keys — vienen de local.properties, nunca del código
        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("supabase.anon.key", "")}\"")
        // URL pública del panel web (Términos, Privacidad, Eliminar cuenta). Sin barra final.
        buildConfigField("String", "WEB_BASE_URL",
            "\"${localProps.getProperty("web.base.url", "").trim().trimEnd('/')}\"")
        manifestPlaceholders["crashlyticsEnabled"] = "true"
        manifestPlaceholders["MAPS_API_KEY"] =
            localProps.getProperty("maps.api.key", "")    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile     = file(keystoreProps.getProperty("storeFile", ""))
                storePassword = keystoreProps.getProperty("storePassword", "")
                keyAlias      = keystoreProps.getProperty("keyAlias", "")
                keyPassword   = keystoreProps.getProperty("keyPassword", "")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            // Sin reportes de crash durante desarrollo
            manifestPlaceholders["crashlyticsEnabled"] = "false"
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
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navegación, ViewModels, imágenes, iconos, fuentes
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.text.google.fonts)

    // Supabase — auth, base de datos, tiempo real, storage, functions
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)

    // AppCompat — necesario para SplashActivity
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Firebase — notificaciones push FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    // Google Maps — tracking en tiempo real
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // Ktor — motor HTTP que usa Supabase internamente (OkHttp soporta WebSockets)
    implementation(libs.ktor.okhttp)

    // Serialización JSON — necesaria para los modelos de Supabase
    implementation(libs.kotlin.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
