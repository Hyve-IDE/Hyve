package com.hyve.mod.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.util.execution.ParametersListUtil
import com.hyve.mod.HytaleVersions
import java.io.File

class HytaleServerRunState(
    environment: ExecutionEnvironment,
    private val config: HytaleServerRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        if (config.installPath.isBlank()) {
            throw ExecutionException("Hytale install path is not configured")
        }

        val serverDir = File(config.installPath, "Server")
        val serverJar = File(serverDir, "HytaleServer.jar")
        if (!serverJar.isFile) {
            throw ExecutionException("HytaleServer.jar not found at ${serverJar.absolutePath}")
        }

        val javaExe = resolveJava()
        val cmd = GeneralCommandLine(javaExe)

        // Debug agent
        if (config.enableDebug) {
            cmd.addParameter("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${config.debugPort}")
        }

        // User VM args (quote-aware parsing)
        if (config.vmArgs.isNotBlank()) {
            cmd.addParameters(ParametersListUtil.parse(config.vmArgs))
        }

        cmd.addParameter("-jar")
        cmd.addParameter("HytaleServer.jar")

        // Program args (quote-aware parsing)
        if (config.programArgs.isNotBlank()) {
            cmd.addParameters(ParametersListUtil.parse(config.programArgs))
        }

        cmd.workDirectory = serverDir
        cmd.charset = Charsets.UTF_8

        val handler = OSProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    private fun resolveJava(): String {
        val targetMajor = HytaleVersions.JDK
        val allJdks = ProjectJdkTable.getInstance().allJdks
        val match = allJdks
            .filter { it.sdkType is JavaSdkType }
            .firstOrNull { jdk ->
                val version = jdk.versionString ?: return@firstOrNull false
                parseMajorVersion(version)?.let { it >= targetMajor } == true
            }
        if (match != null) {
            val homePath = match.homePath
            if (homePath != null) {
                val javaBin = File(homePath, "bin/java")
                if (javaBin.isFile) return javaBin.absolutePath
                val javaExe = File(homePath, "bin/java.exe")
                if (javaExe.isFile) return javaExe.absolutePath
            }
        }
        // Fallback: java on PATH
        return "java"
    }

    companion object {
        private val MAJOR_VERSION_REGEX = Regex("""(?:^|version\s+\"?)(\d+)""")

        fun parseMajorVersion(versionString: String): Int? {
            return MAJOR_VERSION_REGEX.find(versionString)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
