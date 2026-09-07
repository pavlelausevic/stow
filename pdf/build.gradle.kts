// SPDX-License-Identifier: Apache-2.0

/*
 * :pdf je čist Kotlin/JVM modul, bez ijedne zavisnosti i bez ijednog Android tipa.
 * Ta izolacija je namerna: PDF pisac se testira kao obična biblioteka — testovi
 * parsiraju izlazne bajtove — i ne može slučajno da posegne za Android API-jem.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}
