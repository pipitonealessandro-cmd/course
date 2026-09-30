// Standalone build for the pure-JVM YouTube Music client, so it can be unit-tested
// without the Android SDK. The app compiles these sources directly (see app/build.gradle.kts).
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "innertube"
