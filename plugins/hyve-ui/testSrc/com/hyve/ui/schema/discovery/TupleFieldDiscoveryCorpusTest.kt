// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema.discovery

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Corpus-based integration tests for [TupleFieldDiscovery].
 *
 * These tests require access to the vanilla Hytale .ui corpus.
 * They are silently skipped when the corpus directory is unavailable.
 */
class TupleFieldDiscoveryCorpusTest {

    companion object {
        const val CORPUS_PATH_PROPERTY = "hyve.test.corpus.path"
        const val DEFAULT_CORPUS_PATH = "D:/Roaming/install/release/package/game/latest/Client/Data/Game/Interface/"
    }

    private lateinit var corpusDir: File
    private lateinit var result: TupleFieldResult

    @Before
    fun setUp() {
        val path = System.getProperty(CORPUS_PATH_PROPERTY) ?: DEFAULT_CORPUS_PATH
        corpusDir = File(path)
        Assume.assumeTrue(
            "Corpus directory not available at $path — skipping corpus tests",
            corpusDir.exists() && corpusDir.isDirectory
        )

        result = TupleFieldDiscovery().discoverFromDirectory(corpusDir)
    }

    @Test
    fun `corpus discovery finds Style tuple fields`() {
        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()
        assertThat(styleFields).isNotEmpty()

        val fieldNames = styleFields!!.map { it.name }
        assertThat(fieldNames).contains("FontSize", "TextColor")
    }

    @Test
    fun `corpus Style fields include common properties`() {
        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fieldNames = styleFields!!.map { it.name }.toSet()
        // These are commonly used sub-fields of Style tuples in Hytale .ui files
        assertThat(fieldNames).containsAnyOf(
            "FontSize", "TextColor", "RenderBold",
            "VerticalAlignment", "HorizontalAlignment"
        )
    }

    @Test
    fun `corpus Background fields include TexturePath and Border`() {
        val bgFields = result.fieldsByProperty["Background"]
        assertThat(bgFields).isNotNull()

        val fieldNames = bgFields!!.map { it.name }.toSet()
        assertThat(fieldNames).containsAnyOf("TexturePath", "Border")
    }

    @Test
    fun `corpus discovery processes at least 100 files`() {
        assertThat(result.sourceFiles).isGreaterThanOrEqualTo(100)
    }
}
