// Top-level build file. AGP 9 has Kotlin support built in, so only the Compose compiler plugin is added.
plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
