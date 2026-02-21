plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.hyve"
version = "1.0.0"

repositories {
    mavenCentral()
}

val hytaleInstallPath: String by project

dependencies {
    compileOnly(files("$hytaleInstallPath/Server/HytaleServer.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
}

tasks.shadowJar {
    archiveBaseName.set("hyve-hotreload")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.register<Copy>("deployMod") {
    group = "hytale"
    description = "Builds the mod and copies it to the Hytale server mods folder."
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into("$hytaleInstallPath/Server/mods")
}

tasks.register("cleanDeploy") {
    group = "hytale"
    description = "Cleans, rebuilds, and deploys the mod."
    dependsOn("clean", "deployMod")
}

tasks.named("deployMod") {
    mustRunAfter("clean")
}
