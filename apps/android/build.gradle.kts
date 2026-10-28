buildscript {
    configurations.all { resolutionStrategy.force("com.squareup:javapoet:1.13.0") }
}

plugins {
    id("com.android.application") version "8.7.3" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
