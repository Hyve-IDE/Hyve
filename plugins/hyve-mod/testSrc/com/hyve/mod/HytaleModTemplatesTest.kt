package com.hyve.mod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HytaleModTemplatesTest {

    // --- Name conversion tests ---

    @Test
    fun `toModId converts simple name`() {
        assertEquals("my-mod", HytaleModTemplates.toModId("my-mod"))
    }

    @Test
    fun `toModId lowercases and kebabs`() {
        assertEquals("my-cool-mod", HytaleModTemplates.toModId("My Cool Mod"))
    }

    @Test
    fun `toModId strips leading and trailing dashes`() {
        assertEquals("test", HytaleModTemplates.toModId("--test--"))
    }

    @Test
    fun `toModId collapses non-alphanumeric`() {
        assertEquals("hello-world", HytaleModTemplates.toModId("hello___world"))
    }

    @Test
    fun `toMainClassName from simple id`() {
        assertEquals("MyModPlugin", HytaleModTemplates.toMainClassName("my-mod"))
    }

    @Test
    fun `toMainClassName from underscored id`() {
        assertEquals("CoolStuffPlugin", HytaleModTemplates.toMainClassName("cool_stuff"))
    }

    @Test
    fun `toMainClassName single word`() {
        assertEquals("ExamplePlugin", HytaleModTemplates.toMainClassName("example"))
    }

    @Test
    fun `toDisplayName from kebab`() {
        assertEquals("My Cool Mod", HytaleModTemplates.toDisplayName("my-cool-mod"))
    }

    @Test
    fun `toDisplayName from underscore`() {
        assertEquals("My Cool Mod", HytaleModTemplates.toDisplayName("my_cool_mod"))
    }

    // --- Template content tests ---

    private val baseCtx = ModTemplateContext(
        modId = "test-mod",
        groupId = "com.example",
        artifactId = "test-mod",
        packageName = "com.example.testmod",
        mainClassName = "TestModPlugin",
        displayName = "Test Mod",
        language = "java",
        modVersion = "1.0.0",
        authorName = "TestUser",
        authorEmail = "test@example.com",
        authorUrl = "https://example.com",
        description = "A test mod",
        license = "MIT",
        hytaleInstallPath = "C:/Games/Hytale",
    )

    @Test
    fun `buildGradleKts contains shadow plugin`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx)
        assertTrue(content.contains("com.gradleup.shadow"))
        assertTrue(content.contains(HytaleVersions.SHADOW_PLUGIN))
    }

    @Test
    fun `buildGradleKts uses java plugin for java language`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx)
        assertTrue(content.contains("    java"))
        assertTrue(!content.contains("kotlin(\"jvm\")"))
    }

    @Test
    fun `buildGradleKts uses kotlin plugin for kotlin language`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx.copy(language = "kotlin"))
        assertTrue(content.contains("kotlin(\"jvm\")"))
        assertTrue(content.contains(HytaleVersions.KOTLIN))
    }

    @Test
    fun `buildGradleKts adds kotlin jvmToolchain for kotlin`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx.copy(language = "kotlin"))
        assertTrue(content.contains("jvmToolchain(${HytaleVersions.JDK})"))
    }

    @Test
    fun `buildGradleKts references HytaleServer jar`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx)
        assertTrue(content.contains("HytaleServer.jar"))
    }

    @Test
    fun `settingsGradleKts uses artifact name`() {
        val content = HytaleModTemplates.settingsGradleKts(baseCtx)
        assertTrue(content.contains("test-mod"))
    }

    @Test
    fun `gradleProperties contains install path with forward slashes`() {
        val ctx = baseCtx.copy(hytaleInstallPath = "C:\\Games\\Hytale")
        val content = HytaleModTemplates.gradleProperties(ctx)
        assertTrue(content.contains("C:/Games/Hytale"))
        assertTrue(!content.contains("\\"))
    }

    @Test
    fun `manifestJson contains mod metadata`() {
        val content = HytaleModTemplates.manifestJson(baseCtx)
        assertTrue(content.contains("\"test-mod\""))
        assertTrue(content.contains("\"Test Mod\""))
        assertTrue(content.contains("\"TestUser\""))
        assertTrue(content.contains("\"test@example.com\""))
        assertTrue(content.contains("com.example.testmod.TestModPlugin"))
    }

    @Test
    fun `manifestJson omits optional fields when blank`() {
        val ctx = baseCtx.copy(authorEmail = "", authorUrl = "", description = "")
        val content = HytaleModTemplates.manifestJson(ctx)
        assertTrue(!content.contains("\"email\""))
        assertTrue(!content.contains("\"url\""))
        assertTrue(!content.contains("\"description\""))
    }

    @Test
    fun `mainClassJava has correct package and class`() {
        val content = HytaleModTemplates.mainClassJava(baseCtx)
        assertTrue(content.contains("package com.example.testmod;"))
        assertTrue(content.contains("public class TestModPlugin extends JavaPlugin"))
    }

    @Test
    fun `mainClassKotlin has correct package and class`() {
        val content = HytaleModTemplates.mainClassKotlin(baseCtx)
        assertTrue(content.contains("package com.example.testmod"))
        assertTrue(content.contains("class TestModPlugin : JavaPlugin()"))
    }

    @Test
    fun `buildGradleKts contains deployMod task`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx)
        assertTrue(content.contains("tasks.register<Copy>(\"deployMod\")"))
        assertTrue(content.contains("dependsOn(tasks.shadowJar)"))
        assertTrue(content.contains("\$hytaleInstallPath/Server/mods"))
    }

    @Test
    fun `buildGradleKts contains cleanDeploy task`() {
        val content = HytaleModTemplates.buildGradleKts(baseCtx)
        assertTrue(content.contains("tasks.register(\"cleanDeploy\")"))
        assertTrue(content.contains("mustRunAfter(\"clean\")"))
    }

    @Test
    fun `runServerConfiguration uses correct main class and flags`() {
        val content = HytaleModTemplates.runServerConfiguration(baseCtx)
        assertTrue(content.contains("com.hypixel.hytale.Main"))
        assertTrue(content.contains("--mods C:/Games/Hytale/Server/mods"))
        assertTrue(content.contains("--disable-sentry"))
        assertTrue(content.contains("--allow-op"))
        assertTrue(content.contains("deployMod"))
        assertTrue(!content.contains("HytaleServer.jar"))
    }

    @Test
    fun `debugServerConfiguration includes debug agent`() {
        val content = HytaleModTemplates.debugServerConfiguration(baseCtx)
        assertTrue(content.contains("agentlib:jdwp"))
        assertTrue(content.contains("address=*:5005"))
        assertTrue(content.contains("com.hypixel.hytale.Main"))
        assertTrue(content.contains("deployMod"))
    }

    @Test
    fun `buildModConfiguration runs shadowJar`() {
        val content = HytaleModTemplates.buildModConfiguration()
        assertTrue(content.contains("GradleRunConfiguration"))
        assertTrue(content.contains("shadowJar"))
    }

    @Test
    fun `licenseText returns null for None`() {
        assertNull(HytaleModTemplates.licenseText("None", "Test"))
    }

    @Test
    fun `licenseText returns MIT for MIT`() {
        val text = HytaleModTemplates.licenseText("MIT", "TestUser")!!
        assertTrue(text.contains("MIT License"))
        assertTrue(text.contains("TestUser"))
    }

    @Test
    fun `licenseText returns Apache for Apache`() {
        val text = HytaleModTemplates.licenseText("Apache-2.0", "TestUser")!!
        assertTrue(text.contains("Apache License"))
    }

    @Test
    fun `gradleWrapperProperties has correct Gradle version`() {
        val content = HytaleModTemplates.gradleWrapperProperties()
        assertTrue(content.contains("gradle-${HytaleVersions.GRADLE}-bin.zip"))
    }

    @Test
    fun `gitignore contains standard entries`() {
        val content = HytaleModTemplates.gitignore()
        assertTrue(content.contains(".gradle/"))
        assertTrue(content.contains("build/"))
        assertTrue(content.contains(".idea/"))
    }

    // --- Escaping tests ---

    @Test
    fun `escapeJson escapes backslashes and quotes`() {
        assertEquals("""hello\\world""", HytaleModTemplates.escapeJson("""hello\world"""))
        assertEquals("""say \"hi\"""", HytaleModTemplates.escapeJson("""say "hi""""))
    }

    @Test
    fun `escapeJson escapes newlines and tabs`() {
        assertEquals("line1\\nline2", HytaleModTemplates.escapeJson("line1\nline2"))
        assertEquals("col1\\tcol2", HytaleModTemplates.escapeJson("col1\tcol2"))
        assertEquals("a\\rb", HytaleModTemplates.escapeJson("a\rb"))
    }

    @Test
    fun `escapeXml escapes ampersand and angle brackets`() {
        assertEquals("a &amp; b", HytaleModTemplates.escapeXml("a & b"))
        assertEquals("&lt;tag&gt;", HytaleModTemplates.escapeXml("<tag>"))
        assertEquals("val=&quot;x&quot;", HytaleModTemplates.escapeXml("val=\"x\""))
    }

    @Test
    fun `manifestJson escapes description with newlines`() {
        val ctx = baseCtx.copy(description = "Line1\nLine2")
        val content = HytaleModTemplates.manifestJson(ctx)
        assertTrue(content.contains("Line1\\nLine2"))
        assertTrue(!content.contains("Line1\nLine2\""))
    }

    @Test
    fun `runServerConfiguration escapes path with ampersand`() {
        val ctx = baseCtx.copy(hytaleInstallPath = "C:/Games&Mods/Hytale")
        val content = HytaleModTemplates.runServerConfiguration(ctx)
        assertTrue(content.contains("Games&amp;Mods"))
    }

    @Test
    fun `runServerConfiguration converts backslash paths`() {
        val ctx = baseCtx.copy(hytaleInstallPath = "C:\\Games\\Hytale")
        val content = HytaleModTemplates.runServerConfiguration(ctx)
        assertTrue(content.contains("C:/Games/Hytale/Server"))
        assertTrue(!content.contains("\\Games\\"))
    }
}
