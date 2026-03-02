# Offline Hytale Documentation

## Problem Statement

Hytale modders need to reference HytaleModding documentation frequently while working in the IDE. Currently they must switch to a browser, which breaks flow. Users with spotty internet or who prefer offline workflows have no way to access docs at all. The existing RAG (Hytale Knowledge) indexes doc content for search but doesn't provide a browsable, readable documentation experience.

## Target Users

Hytale mod developers using the Hyve IDE plugin. They reference docs constantly — setting up environments, looking up API details, checking UI element properties, following guides. Both beginners (following tutorials step-by-step) and experienced modders (quick-referencing API details) benefit from in-IDE docs.

## Current Workarounds

- Switch to browser, navigate to hytalemodding.dev, find the page
- Use the Hytale Knowledge RAG search for snippets (no full doc reading)
- Keep a browser tab permanently open alongside the IDE

## Proposed Solution

An offline documentation system integrated into the Hyve IDE plugin:

1. **Auto-sync on startup** — Background task fetches docs from `HytaleModding/site` GitHub repo using incremental updates (only changed files). Configurable toggle in settings.
2. **MDX-to-MD conversion** — Strips JSX components (`<Callout>`, `<OfficialDocumentationNotice />`) into markdown equivalents. Preserves all standard markdown formatting, code blocks, images, and links.
3. **Local cache** — Converted `.md` files stored under `~/.hyve/knowledge/docs-offline/` organized by the original repo structure.
4. **Tool window (nav tree)** — "Hytale Documentation" entry in the Tool Windows dropdown. Contains a search bar and a tree navigation mirroring the website's sidebar structure (derived from `meta.json` files in the repo).
5. **Editor tab rendering** — Clicking a doc in the tree opens it as a read-only editor tab using IntelliJ's built-in markdown preview. Respects the current IDE theme. Light CSS overrides for callout blocks.
6. **Language toggle** — Switch between available doc translations (en, de-DE, fr-FR, etc.) from the tool window header.
7. **GitHub link** — Button to open the current doc's source on GitHub in the browser.
8. **RAG citation linking** — When Hytale Knowledge search results reference a doc, clicking the result opens the offline doc in an editor tab instead of (or in addition to) showing the snippet.

## Tech Stack

- **Language**: Kotlin
- **Framework**: IntelliJ Platform SDK (tool windows, editor providers, settings, startup activities)
- **UI**: Compose Desktop (tool window tree/search), IntelliJ Markdown Plugin (rendering)
- **Networking**: Java HttpClient (existing pattern in `DocsParser.kt`)
- **Storage**: Local filesystem (`~/.hyve/knowledge/docs-offline/`)
- **Build**: Gradle (existing `hyve-plugin` build)

## Out of Scope (v1)

- Custom Compose-based markdown renderer (using IntelliJ's built-in preview instead)
- Editing docs from within the IDE (read-only only)
- Doc versioning / pinning to a specific Hytale version (always latest from `main`)
- Offline-first without ever syncing (initial sync always required)
- Bundling docs in the plugin JAR (fetched from GitHub at runtime)

## Dependencies & Risk Areas

### Existing Systems Touched
- **`DocsParser.kt`** — Reuse GitHub fetching, frontmatter extraction, and MDX stripping logic. The offline docs converter needs a richer MDX→MD conversion (preserve callouts as blockquotes rather than stripping entirely).
- **`KnowledgeSettings.kt`** — Already has `autoIndexOnStart`, `docsLanguage`, `docsGithubRepo`, `docsGithubBranch` fields. Add `syncOfflineDocsOnStart` toggle.
- **`KnowledgeSearchPanel.kt` / `SearchResultCard.kt`** — Modify click handler for DOCS corpus results to open offline doc editor tab.
- **`hyve-knowledge.xml`** — Register new tool window and startup activity.

### Risk Areas
- **GitHub API rate limits** — Unauthenticated requests are limited to 60/hour. The tree API is 1 call, but fetching ~224 raw files (English) on first sync could hit limits. Mitigation: batch fetches, respect rate limit headers, fall back to cache.
- **Large first sync** — ~224 docs for English alone, more with translations. Mitigation: background task with progress indicator, only fetch selected language.
- **IntelliJ markdown preview availability** — The markdown plugin is bundled with IntelliJ IDEA but may not be in all IDE variants. Mitigation: graceful fallback to plain text if markdown plugin is absent.
- **meta.json format changes** — The nav structure depends on `meta.json` conventions from the HytaleModding repo. Mitigation: defensive parsing, fallback to alphabetical filesystem order.
