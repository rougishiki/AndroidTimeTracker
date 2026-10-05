import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.timetrack.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.timetrack.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.6"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // The build sandbox denies writes to the default ~/.android/debug.keystore,
        // so both build types are signed with a keystore kept inside the project.
        // Regenerate with keytool if the file is ever lost; because it lives in
        // .gitignore, a fresh clone needs `keytool -genkeypair` once.
        create("local") {
            storeFile = file("${rootProject.projectDir}/keystore/timetrack.jks")
            storePassword = "timetrack"
            keyAlias = "timetrack"
            keyPassword = "timetrack"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("local")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("local")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room writes the schema of each database version to app/schemas. That JSON
    // is committed, so a migration can be diffed against the schema Room
    // actually expects instead of being hand-written and hoped for. Without it
    // neither @AutoMigration nor migration tests are possible.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
