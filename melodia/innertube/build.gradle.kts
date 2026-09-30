plugins {
    kotlin("jvm") version "2.1.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Live tests hit YouTube Music; run with -PliveTests=true
    systemProperty("liveTests", providers.gradleProperty("liveTests").orNull ?: "false")
    systemProperty("innertubeBaseUrl", providers.gradleProperty("innertubeBaseUrl").orNull ?: "")
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}
