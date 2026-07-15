import java.util.Properties

plugins {
    id("com.android.application")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { stream ->
        localProperties.load(stream)
    }
}

android {
    namespace = "com.jhaiian.clint"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jhaiian.clint"
        minSdk = 26
        targetSdk = 37
        versionCode = 18
        versionName = "1.0.6"
    }

    val hasSigningConfig = localProperties.getProperty("signingConfig.storePassword") != null

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(localProperties.getProperty("signingConfig.storeFile", "app/release_keystore.jks"))
                storePassword = localProperties.getProperty("signingConfig.storePassword")
                keyAlias = localProperties.getProperty("signingConfig.keyAlias")
                keyPassword = localProperties.getProperty("signingConfig.keyPassword")
            }
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "false")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FDROID", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningConfig) {
                signingConfig = signingConfigs["release"]
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    val hasNativeLibs = fileTree("src/main/jniLibs") { include("**/*.so") }.files.isNotEmpty() ||
            file("src/main/cpp/CMakeLists.txt").exists() ||
            file("CMakeLists.txt").exists() ||
            file("src/main/jni/Android.mk").exists()

    splits {
        abi {
            isEnable = hasNativeLibs
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.webkit:webkit:1.16.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("com.j256.simplemagic:simplemagic:1.17")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
}
