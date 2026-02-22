plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

group = "com.hyve"
version = "1.0.0"

repositories {
    mavenCentral()
}

// ── Source sets: point at monorepo core + mcp-server source trees ────────
// This subproject compiles ONLY the standalone code (no IDE dependencies).

val monorepoRoot = rootProject.projectDir.parentFile  // hyve-ide root

sourceSets {
    main {
        kotlin.srcDirs(
            monorepoRoot.resolve("plugins/hyve-knowledge/core/src"),
            monorepoRoot.resolve("plugins/hyve-knowledge/mcp-server/src"),
        )
    }
    test {
        kotlin.srcDirs(
            monorepoRoot.resolve("plugins/hyve-knowledge/core/testSrc"),
            monorepoRoot.resolve("plugins/hyve-knowledge/mcp-server/testSrc"),
        )
    }
}

// ── Dependencies ─────────────────────────────────────────────────────────

dependencies {
    // MCP SDK (brings kotlinx-coroutines, kotlinx-serialization, kotlinx-io transitively)
    implementation(libs.mcp.sdk)
    implementation(libs.kotlin.logging)

    // Knowledge core deps
    implementation(libs.sqlite.jdbc)
    implementation(libs.jvector)
    implementation(libs.agrona)
    implementation(libs.commons.math3)
    implementation(libs.slf4j.api)
    implementation(libs.snakeyaml)
    implementation(libs.javaparser.core)

    // Test
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ── Kotlin compiler ──────────────────────────────────────────────────────

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

// ── Shadow JAR ───────────────────────────────────────────────────────────

tasks.shadowJar {
    archiveBaseName.set("hyve-knowledge-mcp")
    archiveClassifier.set("")
    archiveVersion.set("")

    manifest {
        attributes("Main-Class" to "com.hyve.knowledge.mcp.standalone.MainKt")
    }

    // Merge service files (SPI) from all JARs
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
