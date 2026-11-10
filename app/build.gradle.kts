import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

kotlin {
    jvmToolchain(17)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}
val releaseKeystoreFilePath =
    (
        System.getenv("KEYSTORE_FILE")
            ?: keystoreProperties.getProperty("storeFile")
    )?.takeIf { it.isNotBlank() }

// The versionCode is derived from versionName, so the Play bundle and the GitHub APK of one
// release always share it. Both are signed with the same key under the same applicationId, so
// a user can move between the two channels; Android refuses a downgrade, so the codes must
// agree. major.minor.patch -> MMmmmppp00 + build, e.g. 2.0.11 -> 20001100. `build` (0-99,
// -PVERSION_CODE_BUILD) is only for re-uploading the same version to Play, which rejects a
// reused versionCode.
val versionCodeBuild = (project.findProperty("VERSION_CODE_BUILD") as String?)?.toInt() ?: 0

fun versionCodeFor(
    versionName: String,
    build: Int,
): Int {
    val (major, minor, patch) = versionName.split('.').map(String::toInt)
    require(minor < 100 && patch < 1000 && build in 0..99) { "Version $versionName+$build does not fit the versionCode scheme" }
    return major * 10_000_000 + minor * 100_000 + patch * 100 + build
}

// Room exports the schema JSON so a migration can be diffed in review. There is no
// androidTest source set, so this is the only artifact that makes a schema change visible.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.neteinstein.smsredirect"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.neteinstein.smsredirect"
        minSdk = 33
        targetSdk = 37
        // release.yml bumps the patch number in this line on every merge to main.
        versionName = "2.0.11"
        versionCode = versionCodeFor(versionName!!, versionCodeBuild)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystoreFilePath != null) {
                storeFile = file(releaseKeystoreFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: keystoreProperties.getProperty("storePassword")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: keystoreProperties.getProperty("keyAlias")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // Two distributions of one app: same applicationId and same signing key, so Android treats
    // them as one app and either can update the other. See domain/model/AppFeatures.kt.
    //   play   -> Google Play bundle. Backup & Restore only; no SEND_SMS, no self-update.
    //   github -> APK on GitHub releases. Every feature, plus the in-app update check.
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("github") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".test"
        }
        release {
            // R8 in full mode: shrink + optimize + obfuscate. Everything this app relies on
            // that is resolved by name at runtime (JavaMail providers, enum constants
            // persisted in Room and in exported backup JSON) is pinned in proguard-rules.pro
            // -- read that file before adding a dependency that uses reflection.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystoreFilePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // android-mail and android-activation both ship these under this exact name (not
            // LICENSE.txt/NOTICE.txt -- AGP's own default excludes already cover those) and
            // collide during merge.
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            // JavaMail builds its provider registry by concatenating these descriptors across
            // artifacts. They must be merged -- excluding or first-picking them makes
            // Session.getStore("imaps") fail at runtime with NoSuchProviderException and no
            // build-time warning.
            merges += "META-INF/mailcap"
            merges += "META-INF/javamail.providers"
            merges += "META-INF/javamail.default.providers"
            merges += "META-INF/javamail.address.map"
            merges += "META-INF/javamail.default.address.map"
        }
    }
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.work:work-runtime-ktx:2.11.2")

    // Android port of JavaMail. Publishes the javax.mail.* namespace (NOT jakarta.mail.*);
    // com.sun.mail:jakarta.mail and org.eclipse.angus:angus-mail are desktop artifacts that
    // pull java.beans and will not run on Android. android-activation is required because
    // MimeMessage/DataHandler need javax.activation, which the platform does not ship.
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")

    implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("app.cash.turbine:turbine:1.2.1")
    testImplementation("org.json:json:20260522")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
