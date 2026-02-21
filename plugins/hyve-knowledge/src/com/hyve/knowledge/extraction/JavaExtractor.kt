// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.github.javaparser.ParseProblemException
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Extracts graph structure (nodes + edges) from decompiled Java source.
 *
 * Creates:
 * - Package nodes
 * - JavaClass nodes + CONTAINS edge from package
 * - EXTENDS / IMPLEMENTS edges
 * - JavaMethod nodes + CONTAINS edge from class (already done by chunker, but this adds class-level nodes)
 */
object JavaExtractor {

    private val log = Logger.getInstance(JavaExtractor::class.java)

    /**
     * Extract graph nodes/edges from chunks and store in the database.
     * Should be called after chunking is complete.
     */
    fun extractAndStore(
        chunks: List<MethodChunk>,
        db: KnowledgeDatabase,
        sourceDir: File,
    ) {
        // Collect unique packages and classes from chunks
        val packageNodes = mutableSetOf<String>()
        val classNodes = mutableMapOf<String, ClassInfo>()

        for (chunk in chunks) {
            packageNodes.add(chunk.packageName)
            if (chunk.className !in classNodes) {
                classNodes[chunk.className] = ClassInfo(
                    className = chunk.className,
                    packageName = chunk.packageName,
                    filePath = chunk.filePath,
                )
            }
        }

        // Parse each unique file for extends/implements info
        val fileToClasses = chunks.groupBy { it.filePath }.keys
        val classRelations = mutableListOf<ClassRelation>()

        for (filePath in fileToClasses) {
            try {
                val file = File(filePath)
                if (!file.exists()) continue
                val cu = StaticJavaParser.parse(file.readText())
                val relPath = file.relativeTo(sourceDir).path.replace('\\', '/')

                cu.accept(object : VoidVisitorAdapter<Void>() {
                    override fun visit(n: ClassOrInterfaceDeclaration, arg: Void?) {
                        val pkg = cu.packageDeclaration.map { it.nameAsString }.orElse("")
                        val fqcn = if (pkg.isNotEmpty()) "$pkg.${n.nameAsString}" else n.nameAsString

                        // extends
                        for (ext in n.extendedTypes) {
                            classRelations.add(ClassRelation(fqcn, ext.nameAsString, "EXTENDS", relPath))
                        }
                        // implements
                        for (impl in n.implementedTypes) {
                            classRelations.add(ClassRelation(fqcn, impl.nameAsString, "IMPLEMENTS", relPath))
                        }

                        super.visit(n, arg)
                    }

                    override fun visit(n: EnumDeclaration, arg: Void?) {
                        val pkg = cu.packageDeclaration.map { it.nameAsString }.orElse("")
                        val fqcn = if (pkg.isNotEmpty()) "$pkg.${n.nameAsString}" else n.nameAsString

                        for (impl in n.implementedTypes) {
                            classRelations.add(ClassRelation(fqcn, impl.nameAsString, "IMPLEMENTS", relPath))
                        }

                        super.visit(n, arg)
                    }
                }, null)
            } catch (e: ParseProblemException) {
                log.debug("Graph extraction parse error for $filePath: ${e.message}")
            } catch (e: Exception) {
                log.warn("Graph extraction error for $filePath: ${e.message}")
            }
        }

        // Write to database
        db.inTransaction { conn ->
            // Insert package nodes
            val pkgPs = conn.prepareStatement(
                "INSERT OR IGNORE INTO nodes (id, node_type, display_name) VALUES (?, 'Package', ?)"
            )
            for (pkg in packageNodes) {
                if (pkg.isBlank()) continue
                pkgPs.setString(1, "pkg:$pkg")
                pkgPs.setString(2, pkg)
                pkgPs.addBatch()
            }
            pkgPs.executeBatch()

            // Insert class nodes
            val classPs = conn.prepareStatement(
                "INSERT OR IGNORE INTO nodes (id, node_type, display_name, file_path, owning_file) VALUES (?, 'JavaClass', ?, ?, ?)"
            )
            for ((fqcn, info) in classNodes) {
                classPs.setString(1, "class:$fqcn")
                classPs.setString(2, fqcn.substringAfterLast('.'))
                classPs.setString(3, info.filePath)
                val relPath = File(info.filePath).relativeTo(sourceDir).path.replace('\\', '/')
                classPs.setString(4, relPath)
                classPs.addBatch()
            }
            classPs.executeBatch()

            // Insert CONTAINS edges (package → class)
            val containsPs = conn.prepareStatement(
                "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type, owning_file_id) VALUES (?, ?, 'CONTAINS', ?)"
            )
            for ((fqcn, info) in classNodes) {
                if (info.packageName.isBlank()) continue
                containsPs.setString(1, "pkg:${info.packageName}")
                containsPs.setString(2, "class:$fqcn")
                val relPath = File(info.filePath).relativeTo(sourceDir).path.replace('\\', '/')
                containsPs.setString(3, relPath)
                containsPs.addBatch()
            }
            containsPs.executeBatch()

            // Insert EXTENDS / IMPLEMENTS edges
            val relPs = conn.prepareStatement(
                "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type, owning_file_id, target_resolved) VALUES (?, ?, ?, ?, ?)"
            )
            for (rel in classRelations) {
                relPs.setString(1, "class:${rel.sourceClass}")
                // Target may be simple name — try to resolve via known classes
                val resolvedTarget = resolveClassName(rel.targetName, classNodes.keys)
                relPs.setString(2, "class:$resolvedTarget")
                relPs.setString(3, rel.relationType)
                relPs.setString(4, rel.owningFile)
                relPs.setInt(5, if (resolvedTarget == rel.targetName) 0 else 1) // unresolved if simple name
                relPs.addBatch()
            }
            relPs.executeBatch()
        }

        log.info("Graph extraction: ${packageNodes.size} packages, ${classNodes.size} classes, ${classRelations.size} relations")
    }

    /**
     * Try to resolve a simple class name to a FQCN using known classes.
     */
    private fun resolveClassName(simpleName: String, knownClasses: Set<String>): String {
        // Already fully qualified?
        if ('.' in simpleName) return simpleName
        // Find matching FQCN
        return knownClasses.firstOrNull { it.endsWith(".$simpleName") } ?: simpleName
    }

    private data class ClassInfo(
        val className: String,
        val packageName: String,
        val filePath: String,
    )

    private data class ClassRelation(
        val sourceClass: String,
        val targetName: String,
        val relationType: String,
        val owningFile: String,
    )
}
