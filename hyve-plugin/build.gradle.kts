import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
}

group = "com.hyve"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// ── Source sets: point at existing plugin source trees ──────────────────────

val root = rootProject.projectDir.parentFile  // monorepo root

sourceSets {
    main {
        kotlin.srcDirs(
            root.resolve("plugins/hyve-common/src"),
            root.resolve("plugins/hyve-ui/src"),
            root.resolve("plugins/hyve-prefab/src"),
            root.resolve("plugins/hyve-mod/src"),
            root.resolve("plugins/hyve-knowledge/src"),
            root.resolve("plugins/hyve-knowledge/core/src"),
            root.resolve("plugins/hyve-knowledge/mcp-server/src"),
        )
        resources.srcDirs(
            // Our merged META-INF lives here
            "src/main/resources",
            // Non-META-INF resources from each plugin
            root.resolve("plugins/hyve-common/resources"),
            root.resolve("plugins/hyve-ui/resources"),
            root.resolve("plugins/hyve-prefab/resources"),
            root.resolve("plugins/hyve-mod/resources"),
            root.resolve("plugins/hyve-knowledge/resources"),
        )
        // Exclude original per-plugin plugin.xml files — we use our merged one
        resources.filter.exclude("META-INF/plugin.xml")
    }
    test {
        kotlin.srcDirs(
            root.resolve("plugins/hyve-ui/testSrc"),
            root.resolve("plugins/hyve-prefab/testSrc"),
            root.resolve("plugins/hyve-mod/testSrc"),
            root.resolve("plugins/hyve-knowledge/testSrc"),
            root.resolve("plugins/hyve-knowledge/core/testSrc"),
            root.resolve("plugins/hyve-knowledge/mcp-server/testSrc"),
        )
    }
}

// ── Dependencies ───────────────────────────────────────────────────────────

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.3.3", useInstaller = false)

        // Bundled plugins required by our code
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.java.decompiler")
        bundledPlugin("com.intellij.java")

        // Compose + Jewel + Skiko (bundled in 2025.3+)
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.compose.runtime.desktop")
        bundledModule("intellij.libraries.skiko")
        bundledModule("intellij.platform.compose")
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.platform.jewel.ui")

        // MCP server plugin (for knowledge base MCP tools)
        bundledPlugin("com.intellij.mcpServer")

        pluginVerifier()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }

    // hyve-knowledge external libraries
    implementation(libs.sqlite.jdbc)
    implementation(libs.javaparser.core)
    implementation(libs.jvector)
    implementation(libs.agrona)
    implementation(libs.commons.math3)
    implementation(libs.slf4j.api)
    implementation(libs.snakeyaml)

    // Test
    testImplementation(libs.junit.jupiter)
}

// ── Workaround: bundledModule doesn't always add lib/modules/ JARs to classpath ──
// Locate the IntelliJ platform path and add compose/jewel JARs manually.

afterEvaluate {
    val platformPath = configurations["intellijPlatformDependency"]
        .resolvedConfiguration.resolvedArtifacts
        .firstOrNull()?.file

    if (platformPath != null) {
        val platformDir = if (platformPath.isDirectory) platformPath else platformPath.parentFile
        val root = generateSequence(platformDir) { it.parentFile }
            .firstOrNull { it.resolve("lib/modules").isDirectory }

        if (root != null) {
            val moduleJars = listOf(
                "intellij.libraries.compose.foundation.desktop.jar",
                "intellij.libraries.compose.runtime.desktop.jar",
                "intellij.libraries.skiko.jar",
                "intellij.platform.compose.jar",
                "intellij.platform.jewel.foundation.jar",
                "intellij.platform.jewel.ideLafBridge.jar",
                "intellij.platform.jewel.ui.jar",
            )
            for (jar in moduleJars) {
                val f = root.resolve("lib/modules/$jar")
                if (f.exists()) {
                    dependencies.add("compileOnly", files(f))
                    logger.lifecycle("Added module JAR to classpath: $jar")
                }
            }
        } else {
            logger.warn("Could not locate IntelliJ platform root from: $platformDir")
        }
    }
}

// ── Kotlin compiler ────────────────────────────────────────────────────────

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=org.jetbrains.jewel.foundation.ExperimentalJewelApi",
        )
    }
}

// ── Plugin metadata ────────────────────────────────────────────────────────

intellijPlatform {
    pluginConfiguration {
        name = "Hyve"
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }

    signing {
        certificateChainFile = file("chain.crt")
        privateKeyFile = file("private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// ── Task tweaks ────────────────────────────────────────────────────────────

tasks {
    buildSearchableOptions {
        enabled = false
    }
}

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ── Copy standalone MCP server JAR into plugin sandbox ────────────────────────

// Use evaluationDependsOn to ensure mcp-server is configured before we reference its tasks
evaluationDependsOn(":mcp-server")

val mcpServerJar = project(":mcp-server").tasks.named("shadowJar")

// Copy MCP server shadow JAR into sandbox (for runIde)
val copyMcpServerJar by tasks.registering(Copy::class) {
    from(mcpServerJar)
    into(layout.buildDirectory.dir("idea-sandbox/plugins/${intellijPlatform.projectName.get()}/lib"))
    dependsOn(tasks.named("prepareSandbox"))
}

tasks.named("prepareSandbox") {
    finalizedBy(copyMcpServerJar)
}

// Include MCP server shadow JAR in the distribution ZIP
tasks.named<Zip>("buildPlugin") {
    from(mcpServerJar) {
        into("lib")
    }
}
