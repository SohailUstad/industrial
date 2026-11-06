plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.homerun.marketplace.customer"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.homerun.marketplace.customer"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        debug {
            val url = providers.gradleProperty("catalogApiUrl").orElse("https://industrial-catalog-1057772201167.asia-southeast1.run.app/").get()
            buildConfigField("String", "CATALOG_API_URL", "\"$url\"")
            val orderUrl = providers.gradleProperty("orderApiUrl").orElse("https://industrial-orders-1057772201167.asia-southeast1.run.app/").get()
            buildConfigField("String", "ORDER_API_URL", "\"$orderUrl\"")
        }
        release {
            isMinifyEnabled = false
            val url = providers.gradleProperty("catalogApiUrl").orElse("https://industrial-catalog-1057772201167.asia-southeast1.run.app/").get()
            buildConfigField("String", "CATALOG_API_URL", "\"$url\"")
            val orderUrl = providers.gradleProperty("orderApiUrl").orElse("https://industrial-orders-1057772201167.asia-southeast1.run.app/").get()
            buildConfigField("String", "ORDER_API_URL", "\"$orderUrl\"")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
