package com.hyve.mod

data class ModTemplateContext(
    val modId: String,
    val groupId: String,
    val artifactId: String,
    val packageName: String,
    val mainClassName: String,
    val displayName: String,
    val language: String,
    val modVersion: String,
    val authorName: String,
    val authorEmail: String,
    val authorUrl: String,
    val description: String,
    val license: String,
    val hytaleInstallPath: String,
)

object HytaleModTemplates {

    fun buildGradleKts(ctx: ModTemplateContext): String = buildString {
        appendLine("plugins {")
        if (ctx.language == "kotlin") {
            appendLine("    kotlin(\"jvm\") version \"${HytaleVersions.KOTLIN}\"")
        } else {
            appendLine("    java")
        }
        appendLine("    id(\"com.gradleup.shadow\") version \"${HytaleVersions.SHADOW_PLUGIN}\"")
        appendLine("}")
        appendLine()
        appendLine("group = \"${ctx.groupId}\"")
        appendLine("version = \"${ctx.modVersion}\"")
        appendLine()
        appendLine("repositories {")
        appendLine("    mavenCentral()")
        appendLine("}")
        appendLine()
        appendLine("val hytaleInstallPath: String by project")
        appendLine()
        appendLine("dependencies {")
        appendLine("    compileOnly(files(\"\$hytaleInstallPath/Server/HytaleServer.jar\"))")
        appendLine("}")
        appendLine()
        appendLine("java {")
        appendLine("    toolchain {")
        appendLine("        languageVersion.set(JavaLanguageVersion.of(${HytaleVersions.JDK}))")
        appendLine("    }")
        appendLine("}")
        if (ctx.language == "kotlin") {
            appendLine()
            appendLine("kotlin {")
            appendLine("    jvmToolchain(${HytaleVersions.JDK})")
            appendLine("}")
        }
        appendLine()
        appendLine("tasks.shadowJar {")
        appendLine("    archiveClassifier.set(\"\")")
        appendLine("    dependencies {")
        appendLine("        exclude(dependency(\"org.jetbrains.kotlin:.*\"))")
        appendLine("    }")
        appendLine("}")
        appendLine()
        appendLine("tasks.register<Copy>(\"deployMod\") {")
        appendLine("    group = \"hytale\"")
        appendLine("    description = \"Builds the mod and copies it to the Hytale server mods folder.\"")
        appendLine("    dependsOn(tasks.shadowJar)")
        appendLine("    from(tasks.shadowJar.flatMap { it.archiveFile })")
        appendLine("    into(\"\$hytaleInstallPath/Server/mods\")")
        appendLine("}")
        appendLine()
        appendLine("tasks.register(\"cleanDeploy\") {")
        appendLine("    group = \"hytale\"")
        appendLine("    description = \"Cleans, rebuilds, and deploys the mod.\"")
        appendLine("    dependsOn(\"clean\", \"deployMod\")")
        appendLine("}")
        appendLine()
        appendLine("tasks.named(\"deployMod\") {")
        appendLine("    mustRunAfter(\"clean\")")
        appendLine("}")
    }

    fun settingsGradleKts(ctx: ModTemplateContext): String = buildString {
        appendLine("rootProject.name = \"${ctx.artifactId}\"")
    }

    fun gradleProperties(ctx: ModTemplateContext): String = buildString {
        appendLine("# Hytale installation path")
        appendLine("# Inherited from IDE settings. Edit for project-specific override.")
        appendLine("hytaleInstallPath=${toForwardSlashes(ctx.hytaleInstallPath)}")
        appendLine()
        appendLine("# Gradle settings")
        appendLine("org.gradle.jvmargs=-Xmx2g -XX:+UseG1GC")
    }

    fun gradleWrapperProperties(): String = buildString {
        appendLine("distributionBase=GRADLE_USER_HOME")
        appendLine("distributionPath=wrapper/dists")
        appendLine("distributionUrl=https\\://services.gradle.org/distributions/gradle-${HytaleVersions.GRADLE}-bin.zip")
        appendLine("networkTimeout=10000")
        appendLine("validateDistributionUrl=true")
        appendLine("zipStoreBase=GRADLE_USER_HOME")
        appendLine("zipStorePath=wrapper/dists")
    }

    fun manifestJson(ctx: ModTemplateContext): String = buildString {
        appendLine("{")
        appendLine("  \"Group\": \"${escapeJson(ctx.groupId)}\",")
        appendLine("  \"Name\": \"${escapeJson(ctx.modId)}\",")
        appendLine("  \"Version\": \"${escapeJson(ctx.modVersion)}\",")
        appendLine("  \"Main\": \"${escapeJson(ctx.packageName)}.${escapeJson(ctx.mainClassName)}\",")
        appendLine("  \"ServerVersion\": \"*\",")
        if (ctx.description.isNotBlank()) {
            appendLine("  \"Description\": \"${escapeJson(ctx.description)}\",")
        }
        appendLine("  \"Authors\": [")
        append("    { \"Name\": \"${escapeJson(ctx.authorName)}\"")
        if (ctx.authorUrl.isNotBlank()) append(", \"Url\": \"${escapeJson(ctx.authorUrl)}\"")
        appendLine(" }")
        appendLine("  ]")
        appendLine("}")
    }

    fun mainClassJava(ctx: ModTemplateContext): String = buildString {
        appendLine("package ${ctx.packageName};")
        appendLine()
        appendLine("import com.hypixel.hytale.server.core.plugin.JavaPlugin;")
        appendLine("import com.hypixel.hytale.server.core.plugin.JavaPluginInit;")
        appendLine("import java.util.logging.Level;")
        appendLine("import javax.annotation.Nonnull;")
        appendLine()
        appendLine("public class ${ctx.mainClassName} extends JavaPlugin {")
        appendLine()
        appendLine("    public ${ctx.mainClassName}(@Nonnull JavaPluginInit init) {")
        appendLine("        super(init);")
        appendLine("    }")
        appendLine()
        appendLine("    @Override")
        appendLine("    protected void start() {")
        appendLine("        getLogger().at(Level.INFO).log(\"${escapeJavaString(ctx.displayName)} started!\");")
        appendLine("    }")
        appendLine()
        appendLine("    @Override")
        appendLine("    protected void shutdown() {")
        appendLine("        getLogger().at(Level.INFO).log(\"${escapeJavaString(ctx.displayName)} shut down.\");")
        appendLine("    }")
        appendLine("}")
    }

    fun mainClassKotlin(ctx: ModTemplateContext): String = buildString {
        appendLine("package ${ctx.packageName}")
        appendLine()
        appendLine("import com.hypixel.hytale.server.core.plugin.JavaPlugin")
        appendLine("import com.hypixel.hytale.server.core.plugin.JavaPluginInit")
        appendLine("import java.util.logging.Level")
        appendLine()
        appendLine("class ${ctx.mainClassName}(init: JavaPluginInit) : JavaPlugin(init) {")
        appendLine()
        appendLine("    override fun start() {")
        appendLine("        logger.at(Level.INFO).log(\"${escapeJavaString(ctx.displayName)} started!\")")
        appendLine("    }")
        appendLine()
        appendLine("    override fun shutdown() {")
        appendLine("        logger.at(Level.INFO).log(\"${escapeJavaString(ctx.displayName)} shut down.\")")
        appendLine("    }")
        appendLine("}")
    }

    fun runServerConfiguration(ctx: ModTemplateContext): String =
        serverConfiguration(ctx, "Run Hytale Server", enableDebug = false)

    fun debugServerConfiguration(ctx: ModTemplateContext): String =
        serverConfiguration(ctx, "Debug Hytale Server", enableDebug = true)

    fun buildModConfiguration(): String = buildString {
        val pd = "\$PROJECT_DIR\$"
        appendLine("""<component name="ProjectRunConfigurationManager">""")
        appendLine("""  <configuration default="false" name="Build Mod" type="GradleRunConfiguration" factoryName="Gradle">""")
        appendLine("""    <ExternalSystemSettings>""")
        appendLine("""      <option name="executionName" />""")
        appendLine("""      <option name="externalProjectPath" value="$pd" />""")
        appendLine("""      <option name="externalSystemIdString" value="GRADLE" />""")
        appendLine("""      <option name="scriptParameters" value="" />""")
        appendLine("""      <option name="taskDescriptions"><list /></option>""")
        appendLine("""      <option name="taskNames">""")
        appendLine("""        <list>""")
        appendLine("""          <option value="shadowJar" />""")
        appendLine("""        </list>""")
        appendLine("""      </option>""")
        appendLine("""      <option name="vmOptions" />""")
        appendLine("""    </ExternalSystemSettings>""")
        appendLine("""    <GradleScriptDebugEnabled>true</GradleScriptDebugEnabled>""")
        appendLine("""    <method v="2" />""")
        appendLine("""  </configuration>""")
        appendLine("""</component>""")
    }

    private fun serverConfiguration(ctx: ModTemplateContext, name: String, enableDebug: Boolean): String = buildString {
        val installPath = toForwardSlashes(ctx.hytaleInstallPath)
        val pd = "\$PROJECT_DIR\$"
        val programArgs = "--assets $installPath/Assets.zip --allow-op --disable-sentry"
        appendLine("""<component name="ProjectRunConfigurationManager">""")
        appendLine("""  <configuration default="false" name="${escapeXml(name)}" type="HytaleServerRunConfiguration">""")
        appendLine("""    <option name="installPath" value="${escapeXml(installPath)}" />""")
        appendLine("""    <option name="enableDebug" value="$enableDebug" />""")
        appendLine("""    <option name="debugPort" value="5005" />""")
        appendLine("""    <option name="vmArgs" value="--enable-native-access=ALL-UNNAMED" />""")
        appendLine("""    <option name="programArgs" value="${escapeXml(programArgs)}" />""")
        appendLine("""    <method v="2">""")
        appendLine("""      <option name="Gradle.BeforeRunTask" enabled="true" tasks="deployMod" externalProjectPath="$pd" />""")
        appendLine("""    </method>""")
        appendLine("""  </configuration>""")
        appendLine("""</component>""")
    }

    fun readme(ctx: ModTemplateContext): String = buildString {
        appendLine("# ${ctx.displayName}")
        appendLine()
        if (ctx.description.isNotBlank()) {
            appendLine(ctx.description)
            appendLine()
        }
        appendLine("A Hytale server mod built with ${if (ctx.language == "kotlin") "Kotlin" else "Java"}.")
        appendLine()
        appendLine("## Building")
        appendLine()
        appendLine("```bash")
        appendLine("./gradlew shadowJar")
        appendLine("```")
        appendLine()
        appendLine("The output JAR will be in `build/libs/`.")
        appendLine()
        appendLine("## Deploying")
        appendLine()
        appendLine("```bash")
        appendLine("./gradlew deployMod")
        appendLine("```")
        appendLine()
        appendLine("Builds the fat JAR and copies it to the Hytale server `mods/` folder.")
        appendLine()
        appendLine("## Running")
        appendLine()
        appendLine("Use the included run configurations in your IDE:")
        appendLine()
        appendLine("- **Run Hytale Server** — Builds, deploys, and starts the server")
        appendLine("- **Debug Hytale Server** — Same as Run, with remote debugger on port 5005")
        appendLine("- **Build Mod** — Compiles without deploying or starting the server")
    }

    fun licenseText(license: String, authorName: String): String? = when (license) {
        "MIT" -> mitLicense(authorName)
        "Apache-2.0" -> apacheLicense()
        "GPL-3.0" -> gplLicense()
        else -> null
    }

    fun gitignore(): String = buildString {
        appendLine(".gradle/")
        appendLine("build/")
        appendLine(".idea/")
        appendLine("*.iml")
        appendLine("out/")
        appendLine("local.properties")
    }

    fun toModId(projectName: String): String {
        return projectName
            .replace(Regex("[^a-zA-Z0-9]+"), "-")
            .replace(Regex("^-+|-+$"), "")
            .lowercase()
    }

    fun toMainClassName(modId: String): String {
        return modId.split(Regex("[-_]"))
            .joinToString("") { it.replaceFirstChar(Char::titlecase) } + "Plugin"
    }

    fun toDisplayName(projectName: String): String {
        return projectName
            .replace(Regex("[-_]+"), " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }

    // --- Escaping helpers ---

    private fun toForwardSlashes(path: String): String = path.replace("\\", "/")

    internal fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun escapeJavaString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    internal fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun mitLicense(authorName: String): String = buildString {
        appendLine("MIT License")
        appendLine()
        appendLine("Copyright (c) ${java.time.Year.now()} $authorName")
        appendLine()
        appendLine("Permission is hereby granted, free of charge, to any person obtaining a copy")
        appendLine("of this software and associated documentation files (the \"Software\"), to deal")
        appendLine("in the Software without restriction, including without limitation the rights")
        appendLine("to use, copy, modify, merge, publish, distribute, sublicense, and/or sell")
        appendLine("copies of the Software, and to permit persons to whom the Software is")
        appendLine("furnished to do so, subject to the following conditions:")
        appendLine()
        appendLine("The above copyright notice and this permission notice shall be included in all")
        appendLine("copies or substantial portions of the Software.")
        appendLine()
        appendLine("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR")
        appendLine("IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,")
        appendLine("FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE")
        appendLine("AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER")
        appendLine("LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,")
        appendLine("OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE")
        appendLine("SOFTWARE.")
    }

    private fun apacheLicense(): String = buildString {
        appendLine("                                 Apache License")
        appendLine("                           Version 2.0, January 2004")
        appendLine("                        http://www.apache.org/licenses/")
        appendLine()
        appendLine("   Licensed under the Apache License, Version 2.0 (the \"License\");")
        appendLine("   you may not use this file except in compliance with the License.")
        appendLine("   You may obtain a copy of the License at")
        appendLine()
        appendLine("       http://www.apache.org/licenses/LICENSE-2.0")
        appendLine()
        appendLine("   Unless required by applicable law or agreed to in writing, software")
        appendLine("   distributed under the License is distributed on an \"AS IS\" BASIS,")
        appendLine("   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.")
        appendLine("   See the License for the specific language governing permissions and")
        appendLine("   limitations under the License.")
    }

    private fun gplLicense(): String = buildString {
        appendLine("                    GNU GENERAL PUBLIC LICENSE")
        appendLine("                       Version 3, 29 June 2007")
        appendLine()
        appendLine(" Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>")
        appendLine(" Everyone is permitted to copy and distribute verbatim copies")
        appendLine(" of this license document, but changing it is not allowed.")
        appendLine()
        appendLine(" See https://www.gnu.org/licenses/gpl-3.0.txt for the full license text.")
    }
}
