// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser.imports

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.ImportAlias
import com.hyve.ui.parser.variables.VariableScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for ImportResolver search path fallback (P0).
 * Validates that when a relative import fails, the resolver tries fallback search paths.
 */
class ImportResolverSearchPathTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private fun minimalDocument(imports: Map<ImportAlias, String> = emptyMap()) = UIDocument(
        root = UIElement(type = ElementType("Group"), id = null, properties = PropertyMap.empty()),
        imports = imports,
        styles = emptyMap(),
        comments = emptyList()
    )

    @Test
    fun `search path resolves import when relative path fails`() {
        // Arrange: main file in projectDir, Common.ui only in vanillaDir (search path)
        val projectDir = tempFolder.newFolder("project")
        val vanillaDir = tempFolder.newFolder("vanilla")

        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        val commonFile = File(vanillaDir, "Common.ui")
        commonFile.writeText("@CommonSize = 42;")

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = listOf(vanillaDir.toPath())
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: no errors, import resolved via search path
        assertThat(errors).isEmpty()
        val resolved = scope.getResolvedImport("Common")
        assertThat(resolved).isNotNull
        assertThat(resolved!!.getVariable("CommonSize")).isNotNull
    }

    @Test
    fun `relative path takes priority over search path`() {
        // Arrange: Common.ui exists in both projectDir and vanillaDir
        val projectDir = tempFolder.newFolder("project")
        val vanillaDir = tempFolder.newFolder("vanilla")

        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        // Local Common.ui has @LocalSize
        File(projectDir, "Common.ui").writeText("@LocalSize = 100;")
        // Vanilla Common.ui has @VanillaSize
        File(vanillaDir, "Common.ui").writeText("@VanillaSize = 200;")

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = listOf(vanillaDir.toPath())
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: relative path wins — @LocalSize present, @VanillaSize absent
        assertThat(errors).isEmpty()
        val resolved = scope.getResolvedImport("Common")
        assertThat(resolved).isNotNull
        assertThat(resolved!!.getVariable("LocalSize")).isNotNull
        assertThat(resolved.getVariable("VanillaSize")).isNull()
    }

    @Test
    fun `multiple search paths tried in order`() {
        // Arrange: Common.ui exists only in second search path
        val projectDir = tempFolder.newFolder("project")
        val searchDir1 = tempFolder.newFolder("search1")
        val searchDir2 = tempFolder.newFolder("search2")

        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        // Only in second search path
        File(searchDir2, "Common.ui").writeText("@Found = 1;")

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = listOf(searchDir1.toPath(), searchDir2.toPath())
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: found via second search path
        assertThat(errors).isEmpty()
        val resolved = scope.getResolvedImport("Common")
        assertThat(resolved).isNotNull
        assertThat(resolved!!.getVariable("Found")).isNotNull
    }

    @Test
    fun `search path with nested import resolves transitively`() {
        // Arrange: Main imports Common.ui (via search path), Common.ui imports Shared.ui (relative)
        val projectDir = tempFolder.newFolder("project")
        val vanillaDir = tempFolder.newFolder("vanilla")

        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        File(vanillaDir, "Shared.ui").writeText("@SharedVar = 99;")
        File(vanillaDir, "Common.ui").writeText("""
            ${'$'}Shared = "Shared.ui";
            @CommonVar = 42;
        """.trimIndent())

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = listOf(vanillaDir.toPath())
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: Common resolved with its own nested import
        assertThat(errors).isEmpty()
        val commonScope = scope.getResolvedImport("Common")
        assertThat(commonScope).isNotNull
        assertThat(commonScope!!.getVariable("CommonVar")).isNotNull
    }

    @Test
    fun `empty search paths list behaves like no fallback`() {
        // Arrange: import path doesn't exist, empty search paths
        val projectDir = tempFolder.newFolder("project")
        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = emptyList()
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: FileNotFound error, no crash
        assertThat(errors).hasSize(1)
        assertThat(errors[0]).isInstanceOf(ImportError.FileNotFound::class.java)
    }

    @Test
    fun `search path variables are accessible via alias dot notation`() {
        // Arrange: Common.ui defines @Style tuple, accessible as $Common.@Style
        val projectDir = tempFolder.newFolder("project")
        val vanillaDir = tempFolder.newFolder("vanilla")

        val mainFile = File(projectDir, "Main.ui")
        mainFile.writeText("Group {}")

        File(vanillaDir, "Common.ui").writeText("""
            @InputBoxBackground = (TexturePath: "input_bg.png", Border: 16);
        """.trimIndent())

        val resolver = ImportResolver(
            baseDirectory = projectDir.toPath(),
            searchPaths = listOf(vanillaDir.toPath())
        )
        val scope = VariableScope(name = "test")
        val doc = minimalDocument(imports = mapOf(ImportAlias("\$Common") to "Common.ui"))

        // Act
        val errors = resolver.resolveImports(doc, mainFile.toPath(), scope)

        // Assert: @InputBoxBackground is a Tuple with TexturePath and Border
        assertThat(errors).isEmpty()
        val commonScope = scope.getResolvedImport("Common")
        assertThat(commonScope).isNotNull
        val styleTuple = commonScope!!.getVariable("InputBoxBackground")
        assertThat(styleTuple).isNotNull
        assertThat(styleTuple).isInstanceOf(com.hyve.ui.core.domain.properties.PropertyValue.Tuple::class.java)
        val tuple = styleTuple as com.hyve.ui.core.domain.properties.PropertyValue.Tuple
        assertThat(tuple.values).containsKey("TexturePath")
        assertThat(tuple.values).containsKey("Border")
    }
}
