// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.bridge

import com.hyve.knowledge.core.logging.LogProvider
import com.intellij.openapi.diagnostic.Logger

class IntelliJLogProvider(clazz: Class<*>) : LogProvider {
    private val log = Logger.getInstance(clazz)

    override fun info(message: String) = log.info(message)
    override fun warn(message: String, throwable: Throwable?) {
        if (throwable != null) log.warn(message, throwable) else log.warn(message)
    }
    override fun error(message: String, throwable: Throwable?) {
        if (throwable != null) log.error(message, throwable) else log.error(message)
    }
    override fun debug(message: String) = log.debug(message)
}
