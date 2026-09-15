import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.gms.googleservices.GoogleServicesPlugin
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.StrictMode

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.google.services)
    alias(libs.plugins.crashlytics)
}

val APP_ID = rootProject.extra["APP_ID"] as String
val VER_CODE = rootProject.extra["VER_CODE"] as Int
val VER_NAME = rootProject.extra["VER_NAME"] as String
val APP_NAME = rootProject.extra["APP_NAME"] as String
val APP_NAME_NO_SPACES = rootProject.extra["APP_NAME_NO_SPACES"] as String

kotlin {
    jvmToolchain(25)
}

android {
//    buildToolsVersion = "37.0.0"

    compileSdk {
        version = release(libs.versions.targetSdk.get().toInt()) {
//            minorApiLevel = libs.versions.sdkMinor.get().toInt()
        }
    }

    defaultConfig {
        applicationId = APP_ID
        namespace = "com.arn.scrobble.androidApp"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = VER_CODE
        versionName = VER_NAME
        base.archivesName = APP_NAME_NO_SPACES
        ndk {
            abiFilters.add("arm64-v8a")
        }
//        ndkVersion = "29.0.14206865"
    }

    buildFeatures {
        aidl = false
        resValues = false
        shaders = false
    }

    buildTypes {
        all {
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = (name == "release")
            }
        }

        getByName("release") {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            versionNameSuffix = " DEBUG"
        }

        create("releaseGithub") {
            initWith(getByName("release"))
            versionNameSuffix = " GH"
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/**/*.txt")
            excludes.add("/META-INF/native-image/**")
            excludes.add("DebugProbesKt.bin")
        }

        jniLibs {
            useLegacyPackaging = false
            // remove the need for heavyweight ndk
            keepDebugSymbols.add("**/*.so")
        }

        bundle {
            language {
                enableSplit = false
            }
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    signingConfigs {
        val localProperties = gradleLocalProperties(rootDir, project.providers)

        val rawPath = localProperties.getProperty("releaseGithub.keystorePath")
            ?: localProperties.getProperty("release.keystorePath")
            ?: System.getenv("KEYSTORE_PATH")
            ?: System.getenv("RELEASE_KEYSTORE_PATH")

        val candidateKeystoreFiles = listOfNotNull(
            rawPath?.let { rootProject.file(it) },
            rawPath?.let { file(it) },
            file("android-keystore.jks"),
            rootProject.file("androidApp/android-keystore.jks"),
            rootProject.file("android-keystore.jks"),
            file("keystore.jks"),
            rootProject.file("keystore.jks")
        )
        val resolvedKeystoreFile = candidateKeystoreFiles.firstOrNull { it.exists() && it.isFile && it.length() > 0 }

        val storePassword = localProperties.getProperty("releaseGithub.storePassword")
            ?: localProperties.getProperty("release.storePassword")
            ?: System.getenv("KEYSTORE_STORE_PASSWORD")
            ?: System.getenv("KEYSTORE_PASSWORD")
            ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")

        val keyAlias = localProperties.getProperty("releaseGithub.alias")
            ?: localProperties.getProperty("release.alias")
            ?: System.getenv("KEYSTORE_ALIAS")
            ?: System.getenv("KEY_ALIAS")
            ?: "pano-key"

        val keyPassword = localProperties.getProperty("releaseGithub.password")
            ?: localProperties.getProperty("release.password")
            ?: System.getenv("KEYSTORE_KEY_PASSWORD")
            ?: System.getenv("KEY_PASSWORD")
            ?: storePassword

        if (resolvedKeystoreFile != null && !storePassword.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
            register("releaseGithub") {
                storeFile = resolvedKeystoreFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.findByName("releaseGithub")
                ?: signingConfigs.getByName("debug")
        }

        getByName("release") {
            signingConfig = signingConfigs.findByName("releaseGithub")
                ?: signingConfigs.getByName("debug")
        }

        getByName("releaseGithub") {
            signingConfig = signingConfigs.findByName("releaseGithub")
                ?: signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(projects.composeApp)
//        "baselineProfile"(project(mapOf("path" to ":baselineprofile")))

    androidTestImplementation(libs.test.uiautomator)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.espresso.core)
    androidTestImplementation(libs.test.junit)
}

baselineProfile {
    dexLayoutOptimization = true
}

googleServices {
    // 'releaseGithub' variant does not need a google-services.json
    missingGoogleServicesStrategy = GoogleServicesPlugin.MissingGoogleServicesStrategy.WARN
}

aboutLibraries {
    offlineMode = true

    collect {
        configPath = File("../aboutLibsConfig")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
        license.strictMode = StrictMode.WARN
        library.duplicationMode = DuplicateMode.MERGE
    }

    export {
        excludeFields = listOf(
            "developers",
            "funding",
            "description",
            "organization",
            "content",
            "connection",
            "developerConnection"
        )

        outputFile =
            file("../composeApp/src/androidMain/composeResources/files/aboutlibraries.json")
    }
}

tasks.register<Copy>("copyGithubReleaseApk") {
    from("build/outputs/apk/releaseGithub")
    into("../dist")
    include("*.apk")
    rename(".*\\.apk", "$APP_NAME_NO_SPACES-android-arm64-v8a.apk")
}

tasks.configureEach {
    when (name) {
        "packageReleaseGithub" -> {
            finalizedBy("copyGithubReleaseApk")
        }

        "exportLibraryDefinitions" -> {
            finalizedBy(":composeApp:copyNonXmlValueResourcesForAndroidMain")
        }

        "packageReleaseGithubResources", "packageReleaseResources" -> {
            finalizedBy("exportLibraryDefinitions")
        }

    }
}