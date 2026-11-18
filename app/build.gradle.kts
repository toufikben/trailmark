plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.toufik.trailmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.toufik.trailmark"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "1.0.0"
    }

    signingConfigs {
        create("sharedDebug") {
            val shared = file("shared-debug.jks")
            if (shared.exists()) {
                storeFile = shared
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            val ks = System.getenv("RELEASE_KEYSTORE_FILE")?.let { rootProject.file(it) }
            val storePw = System.getenv("RELEASE_STORE_PASSWORD")
            val alias = System.getenv("RELEASE_KEY_ALIAS")
            val keyPw = System.getenv("RELEASE_KEY_PASSWORD")
            if (ks != null && ks.exists() && storePw != null && alias != null && keyPw != null) {
                storeFile = ks
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
            }
        }
    }

    buildTypes {
        debug {
            // QA installs use their own package so a store purchase on the main
            // package never hides trial-only UI during testing.
            applicationIdSuffix = ".qa"
            if (file("shared-debug.jks").exists()) {
                signingConfig = signingConfigs.getByName("sharedDebug")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingConfigs.getByName("release").storeFile != null) {
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

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:geo"))
    implementation(project(":engine"))
    implementation(project(":data"))
    implementation(project(":recording"))
    implementation(project(":regions"))
    implementation(project(":analytics"))
    implementation(project(":coach"))
    implementation(project(":portable"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.maplibre.android)
    implementation(libs.billing)
    implementation(libs.play.review)
    implementation(libs.health.connect)
    implementation(libs.graphhopper.core)
    implementation(libs.play.services.wearable)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.camera.video)
    implementation(libs.guava)
runtimeOnly(libs.slf4j.nop)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.org.json)
}
