package com.hyve.mod

import com.hyve.common.settings.HytaleInstallPath
import java.nio.file.Path

/**
 * Thin delegate to [HytaleInstallPath] for backwards compatibility.
 *
 * Existing callers ([HytaleModuleBuilder], [HytaleModWizardStep]) use this unchanged.
 */
object HytaleInstallSettings {

    fun getInstallPath(): Path? = HytaleInstallPath.get()

    fun setInstallPath(path: Path) = HytaleInstallPath.set(path)

    fun isConfigured(): Boolean = HytaleInstallPath.isConfigured()

    fun clear() = HytaleInstallPath.clear()

    fun isValid(): Boolean = HytaleInstallPath.isValid()
}
