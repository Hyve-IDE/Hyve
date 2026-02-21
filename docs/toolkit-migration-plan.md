# Hytale Toolkit -> Hyve IDE Migration Plan

> Generated 2026-02-15. Living document for tracking what to port, what to skip, and how to implement it.

---

## 1. Feature Inventory (What the Toolkit Does Today)

| Feature | Source | Lines | Status in Hyve |
|---------|--------|-------|----------------|
| **Mod project scaffolder** (Maven/Gradle, Java/Kotlin) | `hytale-mod-cli/cli.py` | ~1,800 | Nothing exists |
| **Setup wizard** (9-step PyQt6 GUI) | `hytale-rag/setup_gui_pyqt.py` | ~2,094 | Nothing exists |
| **Decompile server JAR** (Vineflower + post-fix) | setup wizard step 3 | ~200 | Nothing exists |
| **Generate Javadocs** (JDK auto-download) | setup wizard step 4 | ~150 | Nothing exists |
| **RAG database** (LanceDB + Voyage/Ollama embeddings) | `hytale-rag/src/` | ~3,000 | Nothing exists |
| **MCP server** (Claude/Cursor/Windsurf integration) | `hytale-rag/src/servers/mcp/` | ~500 | JetBrains MCP server exists, no Hyve tools registered |
| **Database download + CDN** | setup wizard step 6 | ~200 | Nothing exists |
| **MCP auto-config** for AI tools | `hytale-rag/mcp_config.py` + step 7 | ~300 | **DEFERRED** (separate CLI project) |
| **Documentation site** (Next.js + MDX, 27 langs) | `site/` | large | Out of scope (stays standalone) |

---

## 2. Migration Decisions

### PORT into Hyve IDE (as IntelliJ plugin features)

#### P1: New Hytale Mod Project Wizard
- **What**: IntelliJ "New Project" wizard that scaffolds a complete Hytale mod
- **From**: `hytale-mod-cli/cli.py` templates + validation
- **Target**: New `hyve-mod` plugin
- **API**: `StarterModuleBuilder` (provides built-in Gradle/Maven import, JDK picker, file templates)
- **No external Gradle plugin dependency**: We own classpath config, run config, and manifest generation directly
- **Build system**: Gradle only (v1). Maven deferred — Gradle 9.1 supports JDK 25, tighter IntelliJ integration, no system-scope dep hack, community familiarity from Minecraft modding
- **Templates to generate**:
  - `manifest.json` (mod metadata)
  - Main plugin class (Java or Kotlin)
  - `build.gradle.kts` + `settings.gradle.kts` + `gradle.properties`
  - Gradle wrapper files (gradlew, gradlew.bat, wrapper JAR + properties)
  - `README.md`, `LICENSE`
  - `.run/Run Hytale Server.run.xml` (run configuration)
- **Validation**: mod ID, group/package, version (real-time inline)
- **Smart features**: Auto-detect Hytale install path, JDK 25 enforcement, persisted defaults

#### P2: Hytale Knowledge Base (RAG without AI)
- **What**: Embedded semantic search over decompiled code, game data, client UI, and docs
- **From**: `hytale-rag/src/` (LanceDB + embeddings + search tools)
- **Target**: New `hyve-knowledge` plugin (or feature set within `hyve-common`)
- **Key design**: Standalone feature, no AI required. Search tool window in the IDE.
- **Components**:
  - Vector store abstraction (LanceDB embedded)
  - Embedding provider factory (Voyage AI / Ollama / OpenAI)
  - 4 search indices: code, client UI, game data, docs
  - IntelliJ Tool Window with search UI
  - Background indexing service
- **Data pipeline**: Ingest decompiled source, parse .ui/.xaml, parse game JSON, crawl docs

#### P3: Decompilation Pipeline
- **What**: One-click decompile `HytaleServer.jar` -> browsable Java source
- **From**: Vineflower invocation + `fix_decompiled_file()` post-processing
- **Target**: Action in `hyve-common` or the knowledge base plugin
- **Post-processing regex fixes**:
  - Replace `<unrepresentable>` with `DecompilerPlaceholder`
  - Remove static blocks from interfaces
  - Move field initializations out of static blocks
- **Output**: Decompiled sources added as a library root (browsable + searchable)

#### P4: MCP Tool Registration
- **What**: Expose Hytale knowledge base as MCP tools via JetBrains' existing MCP server
- **From**: `hytale-rag/src/core/tools/` (8 tools)
- **Target**: Register tools via `mcpToolsProvider` extension point
- **Tools to register**: search_code, search_client_code, search_gamedata, search_docs, plus stats variants
- **Benefit**: Any AI client connected to the IDE gets Hytale context for free

### KEEP standalone (do not port)

| Feature | Reason |
|---------|--------|
| Documentation site (`site/`) | Independent web project, has its own deployment |
| MCP auto-config CLI | Explicitly deferred - will be its own project |
| PyQt6 setup wizard | Replaced by IntelliJ-native UX |
| PyInstaller build system | No longer needed |
| HytaleGradle plugin (`app.ultradev.hytalegradle`) | We handle classpath/run/manifest natively in Hyve |

### SKIP (throwaway)

| Feature | Reason |
|---------|--------|
| Javadoc generation | Low value - decompiled source is more useful |
| CDN database download | Hyve will build indices locally from user's install |
| Logger module (`tools/logger.py`) | IntelliJ has its own logging |
| Update checker | IntelliJ handles plugin updates |

---

## 3. Phase 1: New Hytale Mod Wizard (Detailed Design)

### 3.1 IntelliJ API

**Use `StarterModuleBuilder`** (not `GeneratorNewProjectWizard`).

Verified in codebase:
- `StarterModuleBuilder` at `java/idea-ui/src/com/intellij/ide/starters/local/StarterModuleBuilder.kt`
- Provides: `getAssets()` file template system, `importModule()` for build system import, JDK picker via `getMinJavaVersion()`
- Post-creation sequence (lines 392-411): reformat -> open files -> git init -> import
- `GradleStarterModuleImporter` at `plugins/gradle/java/src/starters/` auto-detects `build.gradle.kts` and calls `linkAndRefreshGradleProject()`
- `MavenStarterModuleImporter` at `plugins/maven/src/main/java/org/jetbrains/idea/maven/starters/` auto-detects `pom.xml` and calls `mavenProjectsManager.addManagedFiles()`
- `LanguageLevel.JDK_25` exists at `jps/model-api/src/org/jetbrains/jps/model/java/LanguageLevel.java` line 33
- Reference implementation: DevKit's `IdePluginModuleBuilder`

Register: `<moduleBuilder builderClass="com.hyve.mod.HytaleModuleBuilder"/>` in plugin.xml.

### 3.2 Wizard UX (2 Steps)

**Step 1 — Project Structure** (blocking decisions):

| Field | Default | Validation |
|-------|---------|------------|
| Project Name + Location | Standard IntelliJ | Required |
| Language | Java (radio) | Java / Kotlin |
| Group ID | Last-used, fallback "com.example" | `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$` |
| Display Name | Titlecased from project name | Auto-filled, editable |

**Step 2 — Mod Metadata** (skippable via Ctrl+Enter for experts):

| Field | Default | Notes |
|-------|---------|-------|
| Author Name | System username | |
| Author Email | (empty) | Optional |
| Author URL | (empty) | Optional |
| Description | (empty) | Optional, multiline |
| Version | 1.0.0 | Semver validated |
| License | MIT dropdown | MIT / Apache-2.0 / GPL-3.0 / None |
| Hytale Install Path | Auto-detected | Must contain `Server/HytaleServer.jar` |
| Git init | Checked | Disabled + tooltip if git not installed |

**Hidden auto-derived fields**: Mod ID (kebab-case from project name), Main Class (PascalCase + "Plugin").

### 3.3 Template Matrix

**Versions** (centralized in `HytaleVersions.kt`):

```
JDK = 25
GRADLE = "9.1"
KOTLIN = "2.3.0"
SHADOW_PLUGIN = "8.1.1"
```

**Gradle only (v1)** — Maven support deferred. Rationale: Gradle 9.1 supports JDK 25, deeper IntelliJ integration, no deprecated system-scope dependency hack, Minecraft community familiarity. Can add Maven later via `getProjectTypes()` if anyone asks.

**Both combinations (Gradle-Java, Gradle-Kotlin) generate**:
- `src/main/{java|kotlin}/{group}/{modId}/{MainClass}.{java|kt}` — JavaPlugin subclass
- `src/main/resources/manifest.json` — Hytale mod descriptor
- `build.gradle.kts` (java/kotlin plugin, Shadow, `compileOnly(files(...))` for HytaleServer.jar)
- `settings.gradle.kts`
- `gradle.properties` (hytaleInstallPath, JVM args, JDK path)
- `gradle/wrapper/gradle-wrapper.jar` + `gradle-wrapper.properties`
- `gradlew` + `gradlew.bat`
- `.run/Run Hytale Server.run.xml` — run configuration
- `README.md`, `LICENSE` (if selected)

Note: Gradle wrapper MUST be generated by us — `GradleStarterModuleImporter` expects `gradlew` to exist.

### 3.4 Run Configuration

From CLI's Maven `run-server` profile (cli.py:1010-1033):

```
java --enable-native-access=ALL-UNNAMED -jar HytaleServer.jar
  --assets {hytaleInstallPath}/Assets.zip
  --assets {project}/src/main/resources
  --allow-op
```

Working directory: `{hytaleInstallPath}/Server`

Generate as `.run/Run Hytale Server.run.xml`.

### 3.5 Hytale Path Architecture

**IDE-scoped** (primary): `Tools > Hytale Modding > Default Install Path`
- Uses Java Preferences API (matches existing `AssetSettings.kt` pattern in hyve-ui)
- Shared across all Hytale mod projects
- Auto-detection from standard OS paths:
  - Windows: `%APPDATA%/Hytale Launcher/install/release/package/game/latest`
  - macOS: `~/Library/Application Support/Hytale Launcher/install/...`
  - Linux: `~/.local/share/Hytale Launcher/install/...`
- Validated by checking `{path}/Server/HytaleServer.jar` exists

**Project-scoped** (override): Written to `gradle.properties` or `pom.xml` properties at creation time with comment: `# Inherited from IDE Settings. Edit for project-specific override.`

### 3.6 Validation Rules

| Field | Rule | When |
|-------|------|------|
| Mod ID | `^[a-z][a-z0-9]*([_-][a-z0-9]+)*$`, 3-64 chars | Real-time (auto-derived) |
| Group | `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$` | Real-time |
| Version | `^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$` | Real-time |
| Class Name | `^[A-Z][a-zA-Z0-9]+$`, not reserved | Auto-derived |
| Hytale Path | Dir exists + `Server/HytaleServer.jar` present | On browse/detect |
| Project Location | Writable, no collision | On Next/Finish |

### 3.7 Post-Creation Sequence

1. Generate files via `getAssets()`
2. Reformat generated code (`ReformatCodeProcessor`)
3. Open main class + build file in editor (`openSampleFiles()`)
4. Git init if selected (`GitRepositoryInitializer`)
5. Trigger import (`importModule()` -> Gradle/Maven importer auto-discovers build files)

### 3.8 Key Failure Modes

| Failure | When Detected | Recovery |
|---------|---------------|----------|
| No JDK 25 | Wizard finish | Dialog: download link + manual path picker |
| No Hytale installation | Step 2 validation | Yellow warning, "Configure Later" + setup README |
| Project name collision | On Finish | Modal: Overwrite / Rename / Change location |
| Build import fails (network) | Post-wizard | Notification with Retry |
| Git not installed | Wizard launch | Checkbox disabled with tooltip |
| Permission denied / disk full | File write | Modal with error, browse alternative |
| Path too long (Windows) | Path validation | Warning suggesting shorter path |

### 3.9 Plugin Structure

```
plugins/hyve-mod/
├── resources/
│   ├── META-INF/plugin.xml
│   └── fileTemplates/           # .ft template files
├── src/com/hyve/mod/
│   ├── HytaleModuleBuilder.kt   # extends StarterModuleBuilder
│   ├── HytaleVersions.kt        # centralized version constants
│   ├── HytaleModWizardStep.kt   # custom wizard step (mod metadata)
│   ├── HytalePathDetector.kt    # OS-specific install path detection
│   └── templates/                # template generation helpers
└── testSrc/com/hyve/mod/
    ├── HytaleModuleBuilderTest.kt
    ├── HytalePathDetectorTest.kt
    └── TemplateGeneratorTest.kt
```

Dependencies: `com.intellij.modules.java`, `com.intellij.gradle`, `com.hyve.common`.

---

## 4. GraphRAG Architecture (Decided)

> Decision: **Hybrid GraphRAG with static-analysis graph** — Option C from the original exploration, built as a two-store system (SQLite graph + JVector HNSW vectors). Zero LLM calls in the entire pipeline. Graph edges extracted via AST parsing and JSON traversal, not LLM entity extraction. Deferred community detection to week 2.

### 4.1 Why GraphRAG Over Flat Vector Search

The existing TypeScript toolkit already does flat vector search. Rebuilding that in Kotlin adds no differentiation. The graph is the unique value:

- Modders ask structural queries constantly: "what implements DropProvider?", "what extends BaseItem?", "what calls registerBlock?" — these are O(1) graph lookups vs O(corpus) vector scans.
- The codebase is undocumented. Graph structure captures implicit documentation (inheritance trees, call chains, item-recipe-NPC relationships).
- Code has a naturally deterministic graph. No LLM needed for entity extraction — JavaParser gives us the AST for free.

### 4.2 Corpus Scale (Verified)

| Corpus | Source | Files | Est. Chunks |
|--------|--------|-------|-------------|
| Decompiled Java | `HytaleServer.jar` via Vineflower | 15,805 | ~75K–100K (method-level) |
| Game data JSON | `Assets.zip` (JSONC with comments) | 31,408 | ~31K (item-level) |
| .ui XML | Disk (178) + Assets.zip (77) | 255 | ~5K (element-level) |
| Docs | HytaleModding.dev | ~unknown | ~2K (section-level) |
| **Total** | | **~47K files** | **~110K–140K chunks** |

### 4.3 Two-Store Architecture

```
~/.hyve/knowledge/
├── knowledge.db              # SQLite: metadata, graph, file hashes (~100–200MB)
├── hnsw/
│   ├── code.hnsw            # Java source vectors (~400MB for 100K chunks)
│   ├── ui.hnsw              # .ui XML vectors (~20MB)
│   ├── gamedata.hnsw        # JSON game data vectors (~120MB)
│   └── docs.hnsw            # Documentation vectors (~10MB)
└── index_meta.json          # Provider ID, dimension, JVector version
```

Total: ~700MB–1GB for a full Hytale server corpus.

**Why embeddings are NOT in SQLite**: 100K vectors × 1024d × 4 bytes = 400MB of BLOBs. SQLite can't do partial-read on BLOBs — every cosine query loads all 400MB. HNSW binary files are purpose-built for this. SQLite maps `chunk_id → {text, file_path, node_id}` after HNSW returns top-K vector IDs.

### 4.4 Graph Schema (Thin v1 — 8 Node Types, 8 Edge Types)

Start with the highest-value relationships. Expand in week 2+ as MCP tool requirements clarify.

**Node Types:**

| Type | Stable ID Scheme | Source |
|------|-----------------|--------|
| `JavaClass` | FQCN: `com.hytale.server.inventory.PlayerInventory` | Java AST |
| `JavaMethod` | FQCN + method: `...PlayerInventory#addItem(ItemStack)` | Java AST |
| `Package` | Package path: `com.hytale.server.inventory` | Java AST |
| `UIElement` | `filename#elementId` or `filename/Type[index]` for unnamed | UIParser |
| `GameItem` | Filename stem: `Ore_Copper` | JSON |
| `Recipe` | `recipe:OutputItemId:index` | JSON |
| `NPC` | Filename stem: `Kweebec_Forager` | JSON |
| `DocSection` | `doc:page-slug#heading-anchor` | Markdown |

**Edge Types:**

| Edge | Source → Target | How Extracted |
|------|----------------|---------------|
| `EXTENDS` | JavaClass → JavaClass | AST `extends` clause |
| `IMPLEMENTS` | JavaClass → JavaClass | AST `implements` clause |
| `CONTAINS` | Package → Class, Class → Method | AST hierarchy |
| `CALLS` | JavaMethod → JavaMethod | AST method invocations (cross-file only) |
| `PARENT_OF` | UIElement → UIElement | XML element nesting |
| `DROPS` | NPC → GameItem | Drop table JSON traversal |
| `CRAFTED_FROM` | Recipe → GameItem (inputs) | Recipe JSON inputs |
| `PRODUCES` | Recipe → GameItem (output) | Recipe JSON output |

**Critical design rule**: every edge carries `owningFileId` (FK to `file_hashes` table). Enables O(edges-owned-by-file) incremental deletes instead of O(total-edges). Dangling edges: when a node is deleted, referencing edges are marked `targetResolved = false` rather than cascade-deleted. A background sweep heals them after indexing completes.

### 4.5 SQLite Schema

```sql
CREATE TABLE file_hashes (
    id INTEGER PRIMARY KEY,
    file_path TEXT UNIQUE NOT NULL,
    content_hash TEXT NOT NULL,     -- SHA-256
    corpus_type TEXT NOT NULL,      -- 'code', 'ui', 'gamedata', 'docs'
    last_indexed INTEGER NOT NULL,  -- epoch ms
    status TEXT DEFAULT 'ok'        -- 'ok', 'failed', 'deleted'
);

CREATE TABLE nodes (
    id TEXT PRIMARY KEY,            -- stable ID (FQCN, filename stem, etc.)
    type TEXT NOT NULL,             -- 'JavaClass', 'JavaMethod', etc.
    source_file_id INTEGER REFERENCES file_hashes(id),
    display_name TEXT NOT NULL,     -- human-readable label
    embedding_text TEXT,            -- text chunk that was embedded
    embedding_text_hash TEXT,       -- SHA-256 of embedding_text
    chunk_id INTEGER,              -- maps to HNSW vector_id
    metadata TEXT                   -- JSON blob for extra properties
);

CREATE TABLE edges (
    id INTEGER PRIMARY KEY,
    source_id TEXT NOT NULL REFERENCES nodes(id),
    edge_type TEXT NOT NULL,        -- 'EXTENDS', 'CALLS', etc.
    target_id TEXT NOT NULL,        -- may reference non-existent node (dangling)
    owning_file_id INTEGER NOT NULL REFERENCES file_hashes(id),
    target_resolved INTEGER DEFAULT 1,  -- 0 = dangling
    properties TEXT                 -- JSON blob
);

CREATE INDEX idx_edges_owning ON edges(owning_file_id);
CREATE INDEX idx_edges_target ON edges(target_id);
CREATE INDEX idx_edges_source ON edges(source_id);
CREATE INDEX idx_nodes_source_file ON nodes(source_file_id);

CREATE TABLE communities (         -- populated in week 2
    id INTEGER PRIMARY KEY,
    label TEXT NOT NULL,
    member_count INTEGER,
    summary TEXT
);

CREATE TABLE node_communities (    -- populated in week 2
    node_id TEXT REFERENCES nodes(id),
    community_id INTEGER REFERENCES communities(id),
    PRIMARY KEY (node_id, community_id)
);

CREATE TABLE index_errors (
    file_path TEXT NOT NULL,
    error_message TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
```

### 4.6 Incremental Indexing Algorithm

1. **Hash all source files** — SHA-256, ~0.2s for 15K files at 150MB/s
2. **Compare** against `file_hashes` table → `changed`, `added`, `deleted` sets
3. **Changed files**: delete owned nodes + edges → re-extract → re-insert
4. **Deleted files**: delete owned nodes → mark referencing edges as `targetResolved = false`
5. **Re-embed only** nodes where `embedding_text_hash` changed (method text actually differs)
6. **Background sweep** heals dangling edges in batches of 500

Resumability: hash writes are atomic per-file (same SQLite transaction as chunk insert). Cancel cleanly leaves all completed files indexed. Resume = re-run indexer → skips already-hashed files.

### 4.7 Embedding Provider Abstraction

```kotlin
interface EmbeddingProvider {
    val modelId: String       // "voyage-code-2" or "ollama/nomic-embed-text"
    val dimension: Int        // 1536 or 768
    suspend fun embed(texts: List<String>): List<FloatArray>  // batch
}

sealed class EmbeddingConfig {
    data class VoyageAI(
        val apiKey: String,
        val model: String = "voyage-code-2",   // 1536d
        val batchSize: Int = 128
    ) : EmbeddingConfig()

    data class Ollama(
        val baseUrl: String = "http://localhost:11434",
        val model: String = "nomic-embed-text", // 768d
        val batchSize: Int = 32
    ) : EmbeddingConfig()
}
```

**Dimension mismatch**: HNSW index is dimension-locked. If user switches providers, old index is kept intact and a new index is built. `index_meta.json` records `(providerId, dimension, version)`. On open, if dimension doesn't match, prompt for rebuild. Raw chunk text in SQLite means re-embedding only requires API calls, not re-parsing.

**Error handling**: Ollama — preflight test before BackgroundableTask starts. VoyageAI — exponential backoff on rate limits, detect 401/403 and stop immediately.

---

## 5. Vector Search: JVector

**Decision**: [JVector](https://github.com/datastax/jvector) by DataStax. Pure Java, no JNI, Apache 2.0. Merges DiskANN + HNSW. On-disk persistence via `OnDiskGraphIndex`. Maven: `io.github.jbellis:jvector`.

**Why JVector over alternatives**:

| Library | Verdict | Reason |
|---------|---------|--------|
| JVector | **CHOSEN** | Pure Java, no native deps, handles 100K+ vectors, on-disk persistence |
| hnswlib-java | Rejected | JNI DLL per platform — breaks plugin portability |
| Apache Lucene 10.x | Rejected | IntelliJ only bundles ancient Lucene 2.4.1 (Maven). Adding 8MB for just KNN is overkill |
| sqlite-vss | Rejected | Native extension, same DLL portability problem |
| Brute-force cosine | Rejected | 100K+ chunks makes this non-viable (~500ms/query at 1024d) |

HNSW construction: ~12s for 100K vectors. Load from disk: ~5–10s. Both run asynchronously in background coroutine.

---

## 6. Query Algorithm (No LLM at Query Time)

**Keyword-dispatched routing** with three modes:

| Query Type | Example | Route |
|------------|---------|-------|
| Semantic | "how does inventory management work" | Vector search only |
| Structural | "what extends PlayerInventory" | Graph traversal only |
| Hybrid | "all classes that call ItemDropSystem and how" | Both → RRF merge |

**Structural keyword patterns** (regex):
- `extends|subclass|inherits` → `EXTENDS` traversal
- `implements` → `IMPLEMENTS` traversal
- `calls|invokes` → `CALLS` traversal
- `drops from|dropped by` → `DROPS` traversal
- `recipe for|crafted from` → `CRAFTED_FROM` traversal

**Safety fallback**: if structural keyword found but no matching entity in graph FTS5 index, fall back to vector search (prevents empty results from novel phrasings).

**Hybrid scoring**: Reciprocal Rank Fusion (k=60). Score = `Σ 1/(k + rank_i)` across result lists. Scale-independent — avoids normalization between cosine similarity [0,1] and graph hop distance [0,∞).

**Chunking strategy**:
- Java: **per-method** with class context prefix — `"[CLASS: {fqcn}]\n[METHOD: {sig}]\n{body}"`
- .ui XML: **per-element** with hierarchy breadcrumb
- Game data: **per-item/recipe/NPC** flattened to readable text (not raw JSON)
- Docs: **per-section** (H2/H3 heading + body)

---

## 7. Plugin Architecture

```
hyve-common (existing)
  ├── Compose bridge + HyveComposeToolWindowFactory
  ├── Theme, base classes
  └── Shared services, settings infrastructure

hyve-ui (existing)
  └── .ui file visual editor

hyve-prefab (existing)
  └── .prefab.json structured editor

hyve-mod (DONE - Phase 1)
  ├── HytaleModuleBuilder (StarterModuleBuilder)
  ├── Wizard steps, templates, validation
  ├── Hytale path detection + settings
  └── Run configuration generation

hyve-knowledge (NEW - Phase 2)
  ├── db/
  │   ├── KnowledgeDatabase.kt          # SQLite via xerial/sqlite-jdbc
  │   ├── FileHashTracker.kt            # SHA-256 change detection
  │   └── schema/                        # CREATE TABLE migrations
  ├── embedding/
  │   ├── EmbeddingProvider.kt           # Interface + sealed EmbeddingConfig
  │   ├── OllamaProvider.kt
  │   └── VoyageAIProvider.kt
  ├── index/
  │   ├── HnswIndex.kt                  # JVector wrapper (per-corpus)
  │   ├── IndexerTask.kt                # BackgroundableTask with progress
  │   └── IncrementalIndexer.kt         # Hash-based change detection loop
  ├── extraction/
  │   ├── JavaExtractor.kt              # JavaParser AST → nodes + edges
  │   ├── JavaChunker.kt                # Method-level text chunking
  │   ├── GameDataExtractor.kt          # Assets.zip JSONC → nodes + edges
  │   ├── UIExtractor.kt                # Reuse UIParser → nodes + edges
  │   └── DecompilationFixes.kt         # Regex fixes for Vineflower artifacts
  ├── search/
  │   ├── KnowledgeSearchService.kt     # Unified search entry point
  │   ├── QueryRouter.kt                # Keyword-dispatched routing
  │   ├── GraphTraversal.kt             # SQLite edge walks
  │   └── HybridScorer.kt              # RRF fusion
  ├── ui/
  │   ├── KnowledgeToolWindowFactory.kt # extends SimpleHyveToolWindowFactory
  │   └── SearchResultsPanel.kt         # Compose search UI
  ├── mcp/
  │   └── HytaleMcpToolProvider.kt      # Registers search_code, etc.
  └── settings/
      └── KnowledgeSettings.kt          # PersistentStateComponent
```

Dependencies: `com.hyve.common`, `io.github.jbellis:jvector`, `org.xerial:sqlite-jdbc`, `com.github.javaparser:javaparser-core`.

---

## 8. Build Plan

### Week 1 (3 days — Java Code GraphRAG)

**Day 1 — Foundation:**
- `hyve-knowledge` plugin skeleton (plugin.xml, module setup, dependencies)
- SQLite schema migration (all tables including graph + communities — empty)
- `FileHashTracker` (SHA-256 computation + change detection)
- `JavaChunker` (method-level splitting with decompilation fix regexes — port from TypeScript toolkit)
- `KnowledgeSettings` PersistentStateComponent (provider choice, API key, Ollama URL)
- **Ships**: correct incremental change detection, unit-tested

**Day 2 — Vector Search:**
- `EmbeddingProvider` interface + `OllamaProvider` + `VoyageAIProvider`
- JVector HNSW wrapper (write, load, query, per-corpus index files)
- `IndexerTask` (BackgroundableTask with ProgressIndicator for 15K files)
- `KnowledgeSearchService` (HNSW query → SQLite chunk lookup → ranked results)
- **Ships**: working vector search over Java source via Ollama

**Day 3 — Graph + UI:**
- `JavaExtractor` (EXTENDS, IMPLEMENTS, CONTAINS, CALLS edges from JavaParser AST)
- `QueryRouter` (keyword-dispatched structural → graph, semantic → vector, hybrid → RRF)
- `KnowledgeToolWindowFactory` (search box + results list via `SimpleHyveToolWindowFactory`)
- `HytaleMcpToolProvider` registration: `search_code` tool
- **Ships**: hybrid GraphRAG search with structural + semantic queries

### Week 2+ (Deferred)

- Game data JSON ingestion (JSONC parser + Assets.zip streaming + malformed file workarounds)
- .ui file indexing (element-level chunks)
- HytaleModding.dev docs crawling
- Community detection (JGraphT Louvain + metadata-based summaries)
- More edge types (DROPS, CRAFTED_FROM, cross-corpus heuristic edges)
- MCP tools: `search_gamedata`, `search_ui`, `search_docs`

---

## 9. Open Questions (Resolved)

| Question | Decision |
|----------|----------|
| Embedding provider default | Ollama (free, local) with VoyageAI as opt-in cloud upgrade |
| Index storage location | Global `~/.hyve/knowledge/` — game data doesn't change per project |
| Bundling Vineflower | Download on demand (P3 decompilation pipeline) |
| Incremental on re-decompile | SHA-256 hash diff — only re-index changed files |
| Graph layer timing | Day 3 of week 1 — thin graph (8 edge types) alongside vector |
| HytaleModding.dev docs | Defer to week 2. Crawl live with cache (avoid stale bundles) |

### Remaining Open Questions

1. **JVector version pinning**: v3.1.0 (stable) vs v4.0.0-rc (newer DiskANN features)? Need to test on-disk persistence reliability of each.
2. **Assets.zip streaming**: JSONC parsing + malformed file workarounds are well-documented in the TypeScript toolkit. Porting to Kotlin Jackson requires testing the same edge cases.
3. **Community summary quality**: metadata-based summaries (package names + top method tokens) may be too terse for "global search" use cases. May need to revisit with optional LLM-generated summaries as an upgrade path.
