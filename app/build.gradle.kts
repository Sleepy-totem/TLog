import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load version + update channel metadata from version.properties at project root.
// build.ps1 auto-bumps versionCode on each build.
val versionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val tlogVersionCode = (versionProps.getProperty("versionCode") ?: "1").toInt()
val tlogVersionName = versionProps.getProperty("versionName") ?: "1.0.0"
val tlogUpdateOwner = versionProps.getProperty("updateOwner") ?: ""
val tlogUpdateRepo = versionProps.getProperty("updateRepo") ?: ""

android {
    namespace = "com.tlog"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tlog"
        minSdk = 26
        targetSdk = 35
        versionCode = tlogVersionCode
        versionName = tlogVersionName
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "UPDATE_OWNER", "\"$tlogUpdateOwner\"")
        buildConfigField("String", "UPDATE_REPO", "\"$tlogUpdateRepo\"")
    }

    signingConfigs {
        create("tlogRelease") {
            val keystore = System.getenv("TLOG_KEYSTORE")
                ?: rootProject.file("tlog-release.keystore").absolutePath
            val f = file(keystore)
            if (f.exists()) {
                storeFile = f
                storePassword = System.getenv("TLOG_KEYSTORE_PASSWORD") ?: "tlogkey"
                keyAlias = System.getenv("TLOG_KEY_ALIAS") ?: "tlog"
                keyPassword = System.getenv("TLOG_KEY_PASSWORD") ?: "tlogkey"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("tlogRelease")
            signingConfig = if (releaseSigning.storeFile?.exists() == true)
                releaseSigning else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
    sourceSets["main"].java.srcDirs("src/main/kotlin")

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
