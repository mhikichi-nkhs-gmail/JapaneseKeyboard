plugins {
    id ("com.android.application")
    id ("org.jetbrains.kotlin.android")
    id ("dagger.hilt.android.plugin")
    id ("kotlin-kapt")
    id ("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.kazumaproject.markdownhelperkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kazumaproject.markdownhelperkeyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 193
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

//        externalNativeBuild {
//            cmake {
//                cppFlags = ""
//            }
//        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
        }
//        build {
//            isMinifyEnabled = true
//            isShrinkResources = true
//            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
//        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding =  true
        buildConfig = true

    }
//    viewBinding{
//        enabled true
//    }
}

dependencies {

    implementation ("androidx.core:core-ktx:1.13.1")
    implementation ("androidx.appcompat:appcompat:1.7.0")
    implementation ("com.google.android.material:material:1.12.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation ("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation ("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation ("androidx.preference:preference-ktx:1.2.1")
    implementation (project(":flexbox"))
    implementation (project(":tenkey"))
    implementation (project(":bl"))
    implementation ("androidx.test.ext:junit-ktx:1.2.1")
    implementation (project(":symbol_keyboard"))
    implementation ("com.google.ai.client.generativeai:generativeai:0.9.0")
    testImplementation ("junit:junit:4.13.2")
    androidTestImplementation ("androidx.test.ext:junit:1.2.1")
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.6.1")

    implementation ("androidx.fragment:fragment-ktx:1.8.2")

    //Dagger - Hilt
    val hilt_version = "2.45"
    implementation ("com.google.dagger:hilt-android:$hilt_version")
    kapt ("com.google.dagger:hilt-android-compiler:$hilt_version")
    kapt ("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation("com.google.dagger:hilt-android-testing:$hilt_version")
    kaptTest("com.google.dagger:hilt-android-compiler:$hilt_version")
    implementation ("de.psdev.licensesdialog:licensesdialog:2.1.0")

    implementation ("com.jakewharton.timber:timber:5.0.1")


}
