package com.hyve.mod.run

import com.hyve.common.settings.HytaleInstallPath
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element

class HytaleServerRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : LocatableConfigurationBase<LocatableRunConfigurationOptions>(project, factory, name) {

    var installPath: String = HytaleInstallPath.get()?.toString()?.replace("\\", "/") ?: ""
    var enableDebug: Boolean = false
    var debugPort: Int = 5005
    var vmArgs: String = ""
    var programArgs: String = "--allow-op --disable-sentry"

    /** Override for server jar path; blank = derive from installPath. */
    var serverJarPath: String = ""
    /** Override for assets zip path; blank = derive from installPath. */
    var assetsZipPath: String = ""

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return HytaleServerSettingsEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return HytaleServerRunState(environment, this)
    }

    override fun checkConfiguration() {
        if (installPath.isBlank() && serverJarPath.isBlank()) {
            throw RuntimeConfigurationWarning("Hytale install path is not set")
        }
        // Resolve the server jar: explicit override → installPath-derived → global setting
        val resolvedJar = if (serverJarPath.isNotBlank()) {
            java.io.File(serverJarPath)
        } else {
            val flatJar = java.io.File(installPath, "Server/HytaleServer.jar")
            if (flatJar.isFile) flatJar
            else HytaleInstallPath.serverJarPath()?.toFile()
        }
        if (resolvedJar == null || !resolvedJar.isFile) {
            val display = serverJarPath.ifBlank { "$installPath/Server/HytaleServer.jar" }
            throw RuntimeConfigurationWarning("Server jar not found: $display")
        }
        if (enableDebug && debugPort !in 1..65535) {
            throw RuntimeConfigurationWarning("Debug port must be between 1 and 65535")
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "installPath", installPath)
        JDOMExternalizerUtil.writeField(element, "enableDebug", enableDebug.toString())
        JDOMExternalizerUtil.writeField(element, "debugPort", debugPort.toString())
        JDOMExternalizerUtil.writeField(element, "vmArgs", vmArgs)
        JDOMExternalizerUtil.writeField(element, "programArgs", programArgs)
        JDOMExternalizerUtil.writeField(element, "serverJarPath", serverJarPath)
        JDOMExternalizerUtil.writeField(element, "assetsZipPath", assetsZipPath)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        JDOMExternalizerUtil.readField(element, "installPath")?.let { installPath = it }
        JDOMExternalizerUtil.readField(element, "enableDebug")?.let { enableDebug = it.toBoolean() }
        JDOMExternalizerUtil.readField(element, "debugPort")?.let { debugPort = it.toIntOrNull() ?: 5005 }
        JDOMExternalizerUtil.readField(element, "vmArgs")?.let { vmArgs = it }
        JDOMExternalizerUtil.readField(element, "programArgs")?.let { programArgs = it }
        JDOMExternalizerUtil.readField(element, "serverJarPath")?.let { serverJarPath = it }
        JDOMExternalizerUtil.readField(element, "assetsZipPath")?.let { assetsZipPath = it }
    }
}
