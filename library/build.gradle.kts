plugins {
    id("com.android.library")
    id("maven-publish")
}

android {
    namespace = "com.jahangir.devlogcat"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 35

        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.startup:startup-runtime:1.1.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {

                //  THIS MUST MATCH YOUR GITHUB
                groupId = "com.github.devjhr"
                artifactId = "devlogcat"
                version = "1.0.4"

                from(components["release"])
            }
        }
    }
}