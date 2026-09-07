// SPDX-License-Identifier: Apache-2.0

import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

/*
 * Potpisni kljuc se cita iz keystore.properties, koji je u .gitignore zajedno sa .jks
 * fajlom. Ako ga nema — na tudjem klonu ili na CI-ju — release se i dalje gradi, samo
 * nepotpisan. Build koji pada zato sto stranac nema moj kljuc bio bi besmislen.
 */
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "rs.lausevic.stow"
    compileSdk = 37

    defaultConfig {
        applicationId = "rs.lausevic.stow"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("en", "sr")
    }

    signingConfigs {
        if (signing.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
                // v1 nije potreban: minSdk je 26, a JAR potpis pokriva samo do API 24.
                // v3 nosi rotaciju kljuca — jeftino sada, nemoguce naknadno.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":pdf"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

/*
 * Nulta mrežna sposobnost i nulta runtime dozvola nisu obećanje u README-u nego uslov
 * za build. Zadatak čita SPOJENI manifest — onaj koji stvarno ulazi u APK, sa svime što
 * su biblioteke ubacile — i pada ako u njemu postoji ijedna <uses-permission> linija.
 *
 * Provera se drži cele liste, a ne samo INTERNET-a, jer je odluka H uklonila i podsetnik:
 * aplikacija po dizajnu ne traži nijednu dozvolu, pa je najjača moguća provera i najprostija.
 */
abstract class VerifyNoPermissionsTask : DefaultTask() {

    @get:org.gradle.api.tasks.InputFile
    abstract val mergedManifest: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val report: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile
        val text = manifest.readText()

        val declared = Regex("""<uses-permission[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        val hardwareFeatures = Regex("""<uses-feature[^>]*android:name\s*=\s*"([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        val problems = buildList {
            if (declared.isNotEmpty()) {
                add("Spojeni manifest traži ${declared.size} dozvolu/e: ${declared.joinToString()}")
            }
            if (!text.contains("android:allowBackup=\"false\"")) {
                add("android:allowBackup mora biti false")
            }
        }

        report.get().asFile.writeText(
            buildString {
                appendLine("Manifest: ${manifest.absolutePath}")
                appendLine("uses-permission: ${if (declared.isEmpty()) "nema" else declared.joinToString()}")
                appendLine("uses-feature:    ${if (hardwareFeatures.isEmpty()) "nema" else hardwareFeatures.joinToString()}")
                appendLine("allowBackup:     ${if (text.contains("android:allowBackup=\"false\"")) "false" else "NIJE false"}")
            },
        )

        if (problems.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Stow ne sme da traži nijednu dozvolu:\n" + problems.joinToString("\n") { "  - $it" },
            )
        }

        logger.lifecycle("Stow: spojeni manifest bez dozvola, allowBackup=false.")
    }
}

androidComponents {
    onVariants { variant ->
        val verify = tasks.register<VerifyNoPermissionsTask>(
            "verify${variant.name.replaceFirstChar { it.uppercase() }}HasNoPermissions",
        ) {
            group = "verification"
            description = "Pada ako spojeni manifest za ${variant.name} traži ijednu dozvolu."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
        }
        // `matching` je lenj: ime zadatka za varijantu ne mora da postoji u trenutku
        // konfiguracije, a i dalje hocemo da `assemble` padne pre nego sto APK nastane.
        val assembleName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
        tasks.matching { it.name == assembleName || it.name == "check" }
            .configureEach { dependsOn(verify) }
    }
}
