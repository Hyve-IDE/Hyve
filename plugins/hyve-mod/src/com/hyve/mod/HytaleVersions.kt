package com.hyve.mod

/**
 * Centralized version constants for generated Hytale mod projects.
 *
 * - JDK 25: Required by Hytale server for native access and preview APIs.
 * - Gradle 9.3.1: Latest stable Gradle with JDK 25 toolchain support.
 * - Kotlin 2.3.10: Latest stable Kotlin targeting JDK 25.
 * - Shadow 9.3.1: Latest stable shadow plugin for fat JAR packaging (9.x requires Gradle 9+).
 */
object HytaleVersions {
    const val JDK: Int = 25
    const val GRADLE: String = "9.3.1"
    const val KOTLIN: String = "2.3.10"
    const val SHADOW_PLUGIN: String = "9.3.1"
}
