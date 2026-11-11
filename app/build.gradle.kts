plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rel.adbhelper"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rel.adbhelper"
        minSdk = 26
        targetSdk = 36
        // Overridable from CI: ./gradlew assembleRelease -PversionName=1.2.3 -PversionCode=45
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as String? ?: "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            // BouncyCastle's jars each ship the same META-INF license/notice files.
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE", "META-INF/NOTICE")
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libadb.android) {
        // We bring our own BouncyCastle (bcpkix-jdk18on) for certificate generation; drop the
        // library's own transitive bcprov to avoid a duplicate-class conflict between the two.
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    implementation(libs.conscrypt.android)
    implementation(libs.bouncycastle.bcpkix)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
