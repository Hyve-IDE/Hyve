package com.hyve.mod

import com.intellij.icons.AllIcons
import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorFile
import com.intellij.ide.starters.local.GeneratorEmptyDirectory
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.starters.local.Starter
import com.intellij.ide.starters.local.StarterContextProvider
import com.intellij.ide.starters.local.StarterModuleBuilder
import com.intellij.ide.starters.local.StarterPack
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.JAVA_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.StarterLanguage
import com.intellij.ide.starters.shared.StarterProjectType
import com.intellij.ide.starters.shared.StarterTestRunner
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.util.lang.JavaVersion
import javax.swing.Icon

class HytaleModuleBuilder : StarterModuleBuilder() {

    private val modMetadata = ModMetadata()

    override fun getBuilderId(): String = "hytale-mod"
    override fun getPresentableName(): String = "Hytale Mod"
    override fun getDescription(): String = "Create a new Hytale server mod project"
    override fun getNodeIcon(): Icon = AllIcons.Nodes.Module
    override fun getWeight(): Int = JVM_WEIGHT + 2000

    override fun getProjectTypes(): List<StarterProjectType> = emptyList()
    override fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
    override fun getMinJavaVersion(): JavaVersion = JavaVersion.compose(HytaleVersions.JDK)
    override fun isExampleCodeProvided(): Boolean = false

    override fun getLanguages(): List<StarterLanguage> {
        return listOf(JAVA_STARTER_LANGUAGE, KOTLIN_STARTER_LANGUAGE)
    }

    override fun getStarterPack(): StarterPack {
        return StarterPack("hytale-mod", listOf(
            Starter("hytale-mod", "Hytale Mod", getDependencyConfig("/starters/hytale-mod.pom"), emptyList())
        ))
    }

    override fun createOptionsStep(contextProvider: StarterContextProvider): StarterInitialStep {
        return StarterInitialStep(contextProvider)
    }

    override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
        return arrayOf(HytaleModWizardStep(modMetadata))
    }

    override fun setupModule(module: Module) {
        starterContext.starter = starterContext.starterPack.starters.first()
        starterContext.starterDependencyConfig = loadDependencyConfig()[starterContext.starter?.id]
        super.setupModule(module)
    }

    private fun mainClassPath(): String {
        val isKotlin = starterContext.language.id == "kotlin"
        val ext = if (isKotlin) "kt" else "java"
        val sourceDir = if (isKotlin) "kotlin" else "java"
        val modId = HytaleModTemplates.toModId(starterContext.artifact)
        val mainClassName = HytaleModTemplates.toMainClassName(modId)
        val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
        return "src/main/$sourceDir/$packagePath/$mainClassName.$ext"
    }

    override fun getAssets(starter: Starter): List<GeneratorAsset> {
        val isKotlin = starterContext.language.id == "kotlin"
        val sourceDir = if (isKotlin) "kotlin" else "java"

        val modId = HytaleModTemplates.toModId(starterContext.artifact)
        val installPath = modMetadata.hytaleInstallPath.ifBlank {
            HytaleInstallSettings.getInstallPath()?.toString() ?: ""
        }

        val ctx = ModTemplateContext(
            modId = modId,
            groupId = starterContext.group,
            artifactId = starterContext.artifact,
            packageName = suggestPackageName(starterContext.group, starterContext.artifact),
            mainClassName = HytaleModTemplates.toMainClassName(modId),
            displayName = HytaleModTemplates.toDisplayName(starterContext.artifact),
            language = starterContext.language.id,
            modVersion = modMetadata.modVersion,
            authorName = modMetadata.authorName,
            authorEmail = modMetadata.authorEmail,
            authorUrl = modMetadata.authorUrl,
            description = modMetadata.description,
            license = modMetadata.license,
            hytaleInstallPath = installPath,
        )

        val packagePath = getPackagePath(starterContext.group, starterContext.artifact)
        val assets = mutableListOf<GeneratorAsset>()

        // Main source file
        val mainClassContent = if (isKotlin) {
            HytaleModTemplates.mainClassKotlin(ctx)
        } else {
            HytaleModTemplates.mainClassJava(ctx)
        }
        assets.add(GeneratorFile(mainClassPath(), mainClassContent))

        // Manifest
        assets.add(GeneratorFile("src/main/resources/manifest.json", HytaleModTemplates.manifestJson(ctx)))

        // Build files
        assets.add(GeneratorFile("build.gradle.kts", HytaleModTemplates.buildGradleKts(ctx)))
        assets.add(GeneratorFile("settings.gradle.kts", HytaleModTemplates.settingsGradleKts(ctx)))
        assets.add(GeneratorFile("gradle.properties", HytaleModTemplates.gradleProperties(ctx)))

        // Gradle wrapper
        val standardAssets = StandardAssetsProvider()
        assets.add(GeneratorFile(standardAssets.gradleWrapperPropertiesLocation, HytaleModTemplates.gradleWrapperProperties()))
        assets.addAll(standardAssets.getGradlewAssets())

        // Run configuration
        if (installPath.isNotBlank()) {
            assets.add(GeneratorFile(".run/Run Hytale Server.run.xml", HytaleModTemplates.runServerConfiguration(ctx)))
            assets.add(GeneratorFile(".run/Debug Hytale Server.run.xml", HytaleModTemplates.debugServerConfiguration(ctx)))
            assets.add(GeneratorFile(".run/Build Mod.run.xml", HytaleModTemplates.buildModConfiguration()))
        }

        // README
        assets.add(GeneratorFile("README.md", HytaleModTemplates.readme(ctx)))

        // License
        HytaleModTemplates.licenseText(modMetadata.license, modMetadata.authorName)?.let {
            assets.add(GeneratorFile("LICENSE", it))
        }

        // .gitignore
        if (starterContext.isCreatingNewProject) {
            assets.add(GeneratorFile(".gitignore", HytaleModTemplates.gitignore()))
        }

        // Empty test directory
        assets.add(GeneratorEmptyDirectory("src/test/$sourceDir/$packagePath"))

        return assets
    }

    override fun getFilePathsToOpen(): List<String> {
        return listOf(mainClassPath(), "build.gradle.kts")
    }
}
