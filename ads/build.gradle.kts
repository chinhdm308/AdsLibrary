import org.apache.commons.logging.LogFactory.release

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.chinchin.ads"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // kotlin core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.preference:preference-ktx:1.2.1")

    val lifecycleVersion = "2.2.0"
    implementation("androidx.lifecycle:lifecycle-extensions:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime:$lifecycleVersion")
    //annotationProcessor("androidx.lifecycle:lifecycle-compiler:$lifecycle_version")
    //kapt("androidx.lifecycle:lifecycle-compiler:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.4")

    // retrofit
    //implementation("com.squareup.retrofit2:retrofit:2.9.0")
    //implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    //implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    implementation("com.google.android.ump:user-messaging-platform:3.0.0")

    // appsflyer
    implementation("com.appsflyer:af-android-sdk:6.9.3")// AppsFlyer SDK (Tracking, Analytics,...)
    implementation("com.appsflyer:adrevenue:6.4.3")// Ad Revenue Tracking

    // applovin
    //implementation("com.applovin:applovin-sdk:11.11.3")     // Applovin SDK (Display Ads, Monetization, Tracking, Analytics,...)
    //implementation("com.applovin.mediation:google-adapter:22.2.0.1")

    // admob
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("com.google.android.gms:play-services-ads-identifier:18.1.0")
    //implementation("com.google.android.gms:play-services-appset:16.1.0")
    //implementation("com.google.android.gms:play-services-basement:18.4.0")

    // mediation admob
    implementation("com.google.ads.mediation:facebook:6.17.0.0")
    implementation("com.google.ads.mediation:applovin:12.6.0.0")
    implementation("com.google.ads.mediation:vungle:7.4.0.1")
    implementation("com.google.ads.mediation:pangle:6.1.0.7.0")

    // adjust
    implementation("com.adjust.sdk:adjust-android:4.38.0")
    implementation("com.android.installreferrer:installreferrer:2.2")

    // fb sdk
    //implementation("com.facebook.android:facebook-android-sdk:16.1.2")

    // firebase
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")

    // multidex
    implementation("androidx.multidex:multidex:2.0.1")

    // billing
    implementation("com.android.billingclient:billing:7.0.0")

    // Guava: Google Core Libraries for Java
    //implementation("com.google.guava:guava:32.0.1-jre")

    // image to text
    //implementation("com.google.android.gms:play-services-vision:20.1.3")

    // UI
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.airbnb.android:lottie:6.4.1")
    implementation("com.intuit.sdp:sdp-android:1.1.1")

    // Loading UI
    implementation("com.github.ybq:Android-SpinKit:1.4.0")
}

afterEvaluate {
    publishing {
        repositories {
            mavenLocal()
        }

        publications {
            create<MavenPublication>("release") {
                // Applies the component for the release build variant.
                from(components["release"])

                groupId = "com.chinchin.ads"
                artifactId = "ads"
                version = "1.0.0"
            }
        }
    }
}