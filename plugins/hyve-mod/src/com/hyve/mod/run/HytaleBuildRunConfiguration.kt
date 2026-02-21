package com.hyve.mod.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element

class HytaleBuildRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<LocatableRunConfigurationOptions>(project, factory, name) {

    var gradleTask: String = "shadowJar"
    var extraArgs: String = ""

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return HytaleBuildSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return HytaleBuildRunState(environment, this)
    }

    override fun checkConfiguration() {
        if (gradleTask.isBlank()) {
            throw RuntimeConfigurationError("Gradle task must not be empty")
        }
        val projectDir = project.basePath
            ?: throw RuntimeConfigurationError("Project base path is not available")
        val wrapperName = if (SystemInfo.isWindows) "gradlew.bat" else "gradlew"
        val wrapper = java.io.File(projectDir, wrapperName)
        if (!wrapper.isFile) {
            throw RuntimeConfigurationWarning("Gradle wrapper not found at ${wrapper.absolutePath}")
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "gradleTask", gradleTask)
        JDOMExternalizerUtil.writeField(element, "extraArgs", extraArgs)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        JDOMExternalizerUtil.readField(element, "gradleTask")?.let { gradleTask = it }
        JDOMExternalizerUtil.readField(element, "extraArgs")?.let { extraArgs = it }
    }
}
