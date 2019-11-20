import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.internal.AndroidExtensionsExtension


val kotlin_version: String by extra

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("kotlin-kapt")
}

android {
    compileSdkVersion(29)
    defaultConfig {
        minSdkVersion(23)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0.0-beta01"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))
    implementation("androidx.appcompat:appcompat:1.1.0")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("androidx.test:runner:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")

    implementation("androidx.core:core-ktx:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta3")
    implementation("com.google.android.material:material:1.1.0-alpha08")
    implementation("androidx.viewpager2:viewpager2:1.0.0-beta04")

    val cameraXVersion = "1.0.0-alpha04"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("com.quickbirdstudios:opencv:3.4.1")
    // Lifecycle - ViewModel and LiveData
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.2.0-rc02")
    implementation("androidx.lifecycle:lifecycle-extensions:2.1.0")
    // Anko (As neither android-ktx or splitties has better apis for intent creation)
    val ankoVersion = "0.10.8"
    implementation("org.jetbrains.anko:anko-commons:$ankoVersion")

    implementation("androidx.navigation:navigation-fragment-ktx:2.2.0-rc02")
    implementation("androidx.navigation:navigation-ui-ktx:2.2.0-rc02")
    implementation("androidx.fragment:fragment-ktx:1.1.0")

    // Koin
    implementation("org.koin:koin-androidx-viewmodel:2.0.1")
    implementation("org.koin:koin-android-scope:2.0.1")

    // Glide
    val glideVersion = "4.9.0"
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    kapt("com.github.bumptech.glide:compiler:$glideVersion")

    // Splitties
    val splittiesVersion = "3.0.0-alpha04"
    implementation("com.louiscad.splitties:splitties-appctx:$splittiesVersion")
    implementation("com.louiscad.splitties:splitties-alertdialog-appcompat:$splittiesVersion")
    implementation("com.louiscad.splitties:splitties-views-dsl-material:$splittiesVersion")
    implementation("com.louiscad.splitties:splitties-views-material:$splittiesVersion")
}
