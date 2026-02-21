package com.hyve.mod.run

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

class HytaleServerConfigurationType : SimpleConfigurationType(
    "HytaleServerRunConfiguration",
    "Hytale Server",
    "Run a Hytale dedicated server with optional debug agent",
    NotNullLazyValue.createValue { AllIcons.Nodes.Deploy }
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return HytaleServerRunConfiguration(project, this, "Hytale Server")
    }

    override fun isEditableInDumbMode(): Boolean = true

    companion object {
        val instance: HytaleServerConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(HytaleServerConfigurationType::class.java)
    }
}
