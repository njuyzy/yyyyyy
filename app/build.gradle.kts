import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
}



android {
    namespace = "com.example.Japp"
    compileSdk = 36

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    val amapApiKey: String = (project.findProperty("AMAP_API_KEY") as String?)
        ?: System.getenv("AMAP_API_KEY")
        ?: localProperties.getProperty("AMAP_API_KEY")
        ?: ""

    val releaseSigningProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
    val hasReleaseSigning = releaseSigningProperties.getProperty("storeFile") != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.Japp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    sourceSets {
        getByName("release") {
            jniLibs.directories.add("libs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // The current 11.x bundle only ships ARM native libraries. Keep it for signed
    // release builds, and use the last x86_64-capable map SDK for emulator builds.
    debugImplementation("com.amap.api:3dmap:9.8.3")
    debugImplementation("com.amap.api:search:9.7.1")
    releaseImplementation(files("libs/AMap3DMap_11.1.001_AMapSearch_9.7.4_AMapLocation_11.1.001_20260402.jar"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.datastore.core)
    implementation(libs.swiperefreshlayout)
    testImplementation(libs.junit)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

}
