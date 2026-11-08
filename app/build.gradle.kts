plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yamahamx.editor"
    compileSdk = 34
    // compileSdkPreview = "UpsideDownCake" / 36

    defaultConfig {
        applicationId = "com.alexperfeito.mxcontrol"
        minSdk = 24 // Android 7.0 Nougat (exigido pela Proteção Automática do Google Play)
        targetSdk = 36
        versionCode = 51
        versionName = "1.4.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("mxcontrol-release.jks")
            storePassword = "mxcontrol2026"
            keyAlias = "mxcontrol"
            keyPassword = "mxcontrol2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    applicationVariants.all {
        val variant = this
        val verName = variant.versionName ?: defaultConfig.versionName ?: "1.0.0"
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "mxcontrol-${variant.name}-v${verName}.apk"
            }
    }
}

afterEvaluate {
    val verName = android.defaultConfig.versionName ?: "1.0.0"
    tasks.matching { it.name == "signReleaseBundle" || it.name == "bundleRelease" }.configureEach {
        doLast {
            val bundleDir = layout.buildDirectory.dir("outputs/bundle/release").get().asFile
            val defaultAab = File(bundleDir, "app-release.aab")
            val targetAab = File(bundleDir, "mxcontrol-release-v${verName}.aab")
            if (defaultAab.exists()) {
                defaultAab.copyTo(targetAab, overwrite = true)
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Google Play Billing
    implementation("com.android.billingclient:billing:8.0.0")

    // Google Play In-App Updates
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Debug tooling
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}
