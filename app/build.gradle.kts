plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.compressx.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.compressx.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
                ?: (rootProject.file("local.properties").takeIf { it.exists() }
                    ?.let { java.util.Properties().also { p -> p.load(it.inputStream()) } }
                    ?.getProperty("keystoreFile"))
            val storePass = System.getenv("KEYSTORE_PASSWORD")
                ?: (rootProject.file("local.properties").takeIf { it.exists() }
                    ?.let { java.util.Properties().also { p -> p.load(it.inputStream()) } }
                    ?.getProperty("keystorePassword"))
            val alias = System.getenv("KEY_ALIAS")
                ?: (rootProject.file("local.properties").takeIf { it.exists() }
                    ?.let { java.util.Properties().also { p -> p.load(it.inputStream()) } }
                    ?.getProperty("keyAlias"))
            val keyPass = System.getenv("KEY_PASSWORD")
                ?: (rootProject.file("local.properties").takeIf { it.exists() }
                    ?.let { java.util.Properties().also { p -> p.load(it.inputStream()) } }
                    ?.getProperty("keyPassword"))

            if (keystoreFile != null && storePass != null && alias != null && keyPass != null) {
                storeFile = file(keystoreFile)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // EXIF
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Activity result
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
