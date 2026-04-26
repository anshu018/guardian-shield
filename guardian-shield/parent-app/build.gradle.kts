import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Force androidx.browser to a version compatible with AGP 8.7.3
// supabase-auth pulls in browser:1.9.0 which requires AGP 8.9.1
configurations.all {
    resolutionStrategy {
        force("androidx.browser:browser:1.8.0")
    }
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

android {
    namespace = "com.guardianshield.parent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guardianshield.parent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL",
            "\"${localProps.getProperty("SUPABASE_URL", "https://placeholder.supabase.co")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("SUPABASE_ANON_KEY", "placeholder")}\"")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.material)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)


    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // DataStore
    implementation(libs.datastore)

    // Coroutines
    implementation(libs.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.android)
    implementation(libs.ktor.cio)

    // WebRTC
    implementation(libs.webrtc)

    // OSMDroid
    implementation(libs.osmdroid)

    // Coil
    implementation(libs.coil)

    // Serialization
    implementation(libs.kotlinx.serialization)
}
