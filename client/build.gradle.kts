// Top-level build file. Plugin versions are declared here once and applied
// (without a version) in app/build.gradle.kts.
//
// These four versions are a matched set -- changing one usually means changing
// the others:
//   Gradle 8.5  <-  AGP 8.2.2  <-  Kotlin 1.9.22  ->  Compose Compiler 1.5.10
// If Android Studio's AGP Upgrade Assistant offers a newer set, take it as a
// group rather than bumping one line.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
