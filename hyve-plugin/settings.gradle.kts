rootProject.name = "hyve-plugin"

pluginManagement {
    repositories {
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
        gradlePluginPortal()
        mavenCentral()
    }
}

include("mcp-server")
include("hyve-hotreload")
