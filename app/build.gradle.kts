plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.Japp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.Japp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
<<<<<<< HEAD
    implementation(libs.sdp)
    implementation(libs.ssp)
=======
    implementation(libs.datastore.core)
>>>>>>> 8e17abf98766200ef08a42fca1e64b4600ad7f30
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)



}