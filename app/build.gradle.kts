import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(FileInputStream(versionPropsFile))
    }
}

val vCode = (versionProps["VERSION_CODE"] as? String)?.toInt() ?: 1
val vName = (versionProps["VERSION_NAME"] as? String) ?: "1.0.0"
val cSdk = (versionProps["COMPILE_SDK"] as? String)?.toInt() ?: 35
val mSdk = (versionProps["MIN_SDK"] as? String)?.toInt() ?: 29
val tSdk = (versionProps["TARGET_SDK"] as? String)?.toInt() ?: 35

android {
    namespace = "com.sentinel.nomedia"
    compileSdk = cSdk

    defaultConfig {
        applicationId = "com.sentinel.nomedia"
        minSdk = mSdk
        targetSdk = tSdk
        versionCode = vCode
        versionName = vName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)

    testImplementation(libs.junit)
    testImplementation(libs.google.truth)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
