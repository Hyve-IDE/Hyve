package com.hyve.mod.run

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

class HytaleBuildConfigurationType : SimpleConfigurationType(
    "HytaleBuildRunConfiguration",
    "Hytale Build",
    "Build a Hytale mod using Gradle",
    NotNullLazyValue.createValue { AllIcons.Actions.Compile }
) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return HytaleBuildRunConfiguration(project, this, "Hytale Build")
    }

    override fun isEditableInDumbMode(): Boolean = true

    companion object {
        val instance: HytaleBuildConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(HytaleBuildConfigurationType::class.java)
    }
}
