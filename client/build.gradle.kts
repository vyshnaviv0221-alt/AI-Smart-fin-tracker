// Top-level build file. Plugin versions are declared once here and applied
// (without a version) in app/build.gradle.kts.
//
// Matched set, chosen for the toolchain actually installed on this machine
// (Android Studio's bundled JDK 25, SDK platform 37):
//   Gradle 9.7.1  <-  AGP 9.4.0  <-  Kotlin 2.4.10
//
// AGP 9 has built-in Kotlin support, so `org.jetbrains.kotlin.android` must
// NOT be applied (AGP fails the build if it is). The Compose compiler still
// ships as its own plugin since Kotlin 2.0, replacing the old
// `composeOptions { kotlinCompilerExtensionVersion }` block.
//
// Room uses KSP, not kapt: kapt is explicitly incompatible with AGP's
// built-in Kotlin support.
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
