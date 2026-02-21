// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.logging

interface LogProvider {
    fun info(message: String)
    fun warn(message: String, throwable: Throwable? = null)
    fun error(message: String, throwable: Throwable? = null)
    fun debug(message: String)
}

object StdoutLogProvider : LogProvider {
    override fun info(message: String) {
        System.err.println("[INFO] $message")
    }

    override fun warn(message: String, throwable: Throwable?) {
        System.err.println("[WARN] $message")
        throwable?.printStackTrace(System.err)
    }

    override fun error(message: String, throwable: Throwable?) {
        System.err.println("[ERROR] $message")
        throwable?.printStackTrace(System.err)
    }

    override fun debug(message: String) {
        System.err.println("[DEBUG] $message")
    }
}
