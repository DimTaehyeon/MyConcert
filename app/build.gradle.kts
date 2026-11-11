import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "edu.taehyeon.myconcert"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "edu.taehyeon.myconcert"
        minSdk = 24
        targetSdk = 37
        versionCode = 8
        versionName = "1.2.1"

        val local = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        val kopisKey = providers.environmentVariable("KOPIS_API_KEY").orNull
            ?: local.getProperty("kopis.api.key", "")
        buildConfigField("String", "KOPIS_API_KEY", "\"${kopisKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("org.jsoup:jsoup:1.24.1")
    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.12.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
