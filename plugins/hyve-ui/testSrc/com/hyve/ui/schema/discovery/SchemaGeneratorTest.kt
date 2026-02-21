// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema.discovery

import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * One-time schema generation test — runs the full expanded discovery pipeline
 * against the real Hytale corpus and dumps the result to stdout.
 *
 * Run manually with @Ignore removed, then use the output to update curated-schema.json.
 * This test is @Ignore'd by default because it requires real game files on disk.
 */
class SchemaGeneratorTest {

    @Ignore("Manual: run to regenerate curated-schema.json from real corpus")
    @Test
    fun `generate schema from full corpus`() {
        val gameDir = File("D:/Roaming/install/release/package/game/latest/Client/Data/Game/Interface")
        val editorDir = File("D:/Roaming/install/release/package/game/latest/Client/Data/Editor/Interface")
        val assetsZip = File("D:/Roaming/install/release/package/game/latest/Assets.zip")

        val directories = listOf(gameDir, editorDir).filter { it.isDirectory }
        println("Directories: ${directories.map { "${it.path} (${it.walkTopDown().filter { f -> f.extension.equals("ui", true) }.count()} .ui files)" }}")

        val zipSources = if (assetsZip.exists()) {
            ZipFile(assetsZip).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".ui", ignoreCase = true) && !it.isDirectory }
                    .map { entry -> entry.name to zip.getInputStream(entry).bufferedReader().readText() }
                    .toList()
            }
        } else emptyList()
        println("Assets.zip .ui entries: ${zipSources.size}")

        // Run schema discovery
        val discovery = SchemaDiscovery { println("  $it") }
        val result = discovery.discoverFromSources(directories, zipSources)

        // Run tuple field discovery
        val tupleDiscovery = TupleFieldDiscovery { println("  $it") }
        val tupleResult = tupleDiscovery.discoverFromSources(directories, zipSources)

        println("\n=== DISCOVERY SUMMARY ===")
        println("Source files: ${result.sourceFiles}")
        println("Unique element types: ${result.uniqueElementTypes}")
        println("Total element occurrences: ${result.totalElements}")
        println("Total unique properties: ${result.totalProperties}")
        println("Parse errors: ${result.parseErrors}")
        println()

        // Print all element types with their properties
        for (element in result.elements) {
            println("${element.type} (${element.category}, ${element.occurrences} occurrences, children=${element.canHaveChildren})")
            for (prop in element.properties) {
                val vals = if (prop.observedValues.isNotEmpty()) " values=${prop.observedValues.take(5)}" else ""
                println("  ${prop.name}: ${prop.type} (${prop.occurrences})$vals")
            }
        }

        // Write to file for review
        val outputDir = File(System.getProperty("user.home"), ".hyve")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "discovered-schema-full.json")
        result.toJsonFile(outputFile)
        println("\nFull result written to: ${outputFile.absolutePath}")
    }
}
