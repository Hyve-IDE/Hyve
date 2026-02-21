// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JPasswordField

/**
 * Parent configurable for Hyve Knowledge settings.
 * Shows general settings (paths, auto-index) and hosts two child pages:
 * - Embedder (provider, models)
 * - Search (results per corpus, expansion)
 */
class KnowledgeSettingsConfigurable : SearchableConfigurable, Configurable.Composite {

    override fun getId(): String = "hyve.knowledge.settings"
    override fun getDisplayName(): String = "Hyve Knowledge"

    private val general = GeneralConfigurable()

    override fun getConfigurables(): Array<Configurable> = arrayOf(
        EmbedderConfigurable(),
        SearchConfigurable(),
    )

    override fun createComponent(): JComponent = general.createComponent()!!
    override fun isModified(): Boolean = general.isModified
    override fun apply() = general.apply()
    override fun reset() = general.reset()
    override fun disposeUIResources() = general.disposeUIResources()
}

/**
 * General settings: paths, documentation source, auto-index toggle.
 */
private class GeneralConfigurable : BoundConfigurable("General") {

    private val settings get() = KnowledgeSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        val state = settings.state

        group("Documentation") {
            row("Language:") {
                comboBox(DOCS_LANGUAGES)
                    .bindItem(state::docsLanguage.toNullableProperty())
                    .comment("Language for modding docs (e.g. en, de-DE, fr-FR)")
            }
            row("GitHub Repo:") {
                textField()
                    .columns(COLUMNS_MEDIUM)
                    .bindText(state::docsGithubRepo)
                    .comment("e.g. HytaleModding/site")
            }
            row("Branch:") {
                textField()
                    .columns(COLUMNS_SHORT)
                    .bindText(state::docsGithubBranch)
            }
        }

        group("Paths") {
            row("Decompiled output:") {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Decompile Output Directory")
                ).columns(COLUMNS_LARGE)
                    .bindText(state::decompileOutputPath)
                    .comment("Leave empty for default: ~/.hyve/knowledge/decompiled/")
            }
            row("Index data:") {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("Index Data Directory")
                ).columns(COLUMNS_LARGE)
                    .bindText(state::indexPath)
                    .comment("Leave empty for default: ~/.hyve/knowledge/")
            }
        }

        row {
            checkBox("Auto-rebuild index on IDE startup")
                .bindSelected(state::autoIndexOnStart)
        }
    }

    companion object {
        private val DOCS_LANGUAGES = listOf(
            "en", "de-DE", "fr-FR", "es-ES", "it-IT", "pt-PT", "nl-NL",
            "pl-PL", "sv-SE", "da-DK", "cs-CZ", "hu-HU", "ro-RO",
            "ru-RU", "uk-UA", "tr-TR", "sq-AL", "af-ZA", "lt-LT", "lv-LV",
            "ja-JP", "ar-SA", "hi-IN", "id-ID", "vi-VN", "pt-BR",
        )
    }
}

/**
 * Embedder settings: provider selection, Ollama/VoyageAI model configuration.
 */
class EmbedderConfigurable : BoundConfigurable("Embedder") {

    private val settings get() = KnowledgeSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        val state = settings.state

        group("Provider") {
            buttonsGroup {
                row {
                    radioButton("Ollama (local)", "ollama")
                }
                row {
                    radioButton("VoyageAI (cloud)", "voyage")
                }
            }.bind(state::embeddingProvider)
        }

        group("Ollama") {
            row("Base URL:") {
                textField()
                    .columns(COLUMNS_LARGE)
                    .bindText(state::ollamaBaseUrl)
            }
            row("Code Model:") {
                textField()
                    .columns(COLUMNS_MEDIUM)
                    .bindText(state::ollamaCodeModel)
                    .comment("e.g. qwen3-embedding:8b (for server code)")
            }
            row("Text Model:") {
                textField()
                    .columns(COLUMNS_MEDIUM)
                    .bindText(state::ollamaTextModel)
                    .comment("e.g. nomic-embed-text-v2-moe (for game data, docs, UI)")
            }
        }

        group("VoyageAI") {
            row("API Key:") {
                cell(JPasswordField())
                    .columns(COLUMNS_LARGE)
                    .bindText(state::voyageApiKey)
            }
            row("Code Model:") {
                textField()
                    .columns(COLUMNS_MEDIUM)
                    .bindText(state::voyageCodeModel)
                    .comment("e.g. voyage-code-3")
            }
            row("Text Model:") {
                textField()
                    .columns(COLUMNS_MEDIUM)
                    .bindText(state::voyageTextModel)
                    .comment("e.g. voyage-3-large")
            }
        }
    }
}

/**
 * Search/RAG settings: result limits, cross-corpus expansion.
 */
class SearchConfigurable : BoundConfigurable("Search") {

    private val settings get() = KnowledgeSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        val state = settings.state

        group("Results") {
            row("Results per corpus:") {
                spinner(1..50, 1)
                    .bindIntValue(state::resultsPerCorpus)
                    .comment("Maximum direct results per category (Code, Game Data, Client UI, Docs)")
            }
        }

        group("Cross-Corpus Expansion") {
            row("Max related connections:") {
                spinner(0..20, 1)
                    .bindIntValue(state::maxRelatedConnections)
                    .comment("Graph-expanded results per corpus (0 = disabled)")
            }
        }
    }
}
