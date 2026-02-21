package com.hyve.mod.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.execution.ParametersListUtil
import java.io.File

class HytaleBuildRunState(
    environment: ExecutionEnvironment,
    private val config: HytaleBuildRunConfiguration,
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val projectDir = config.project.basePath
            ?: throw ExecutionException("Project base path is not available")

        val wrapperName = if (SystemInfo.isWindows) "gradlew.bat" else "gradlew"
        val wrapper = File(projectDir, wrapperName)

        val cmd = if (wrapper.isFile) {
            GeneralCommandLine(wrapper.absolutePath)
        } else {
            // Fallback to gradle on PATH
            GeneralCommandLine(if (SystemInfo.isWindows) "gradle.bat" else "gradle")
        }

        cmd.addParameter(config.gradleTask)

        // Quote-aware parsing
        if (config.extraArgs.isNotBlank()) {
            cmd.addParameters(ParametersListUtil.parse(config.extraArgs))
        }

        cmd.workDirectory = File(projectDir)
        cmd.charset = Charsets.UTF_8

        val handler = OSProcessHandler(cmd)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}
