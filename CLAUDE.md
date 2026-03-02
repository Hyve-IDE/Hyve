# Hyve

IntelliJ platform plugin for Hytale game modding, consisting of three sub-systems: a core knowledge library, an IDE plugin, and a standalone MCP server.

## Files

| File | What | When to read |
| ---- | ---- | ------------ |
| `hyve-plugin/build.gradle.kts` | Standalone launcher build config | Modifying launcher packaging or bundled plugin list |
| `hyve-plugin/settings.gradle.kts` | Root settings including all plugin modules | Adding/removing modules, understanding module graph |

## Subdirectories

| Directory | What | When to read |
| --------- | ---- | ------------ |
| `plugins/hyve-common/` | Shared utilities: settings UI, Hytale install path detection, version detection | Modifying settings, adding common utilities |
| `plugins/hyve-knowledge/core/` | Core knowledge library — no IntelliJ deps; shared by IDE plugin and MCP server | Modifying indexers, search, embedding, config, or DB |
| `plugins/hyve-knowledge/src/` | IDE plugin bridge — connects core to IntelliJ services via *Factory and *Bridge classes | Adding IDE-specific features, modifying tool window |
| `plugins/hyve-knowledge/mcp-server/` | Standalone MCP server process — uses McpConfig with env var overrides | Modifying MCP tools or standalone server behavior |
| `plugins/hyve-knowledge/resources/` | Plugin descriptor (plugin.xml) and static resources | Adding extension points, modifying plugin metadata |
| `plugins/hyve-ui/` | Hytale .ui file editor — Compose/Jewel canvas painter and composer | Working on the UI file editor |
| `hyve-plugin/` | Launcher that bundles and distributes all plugins | Modifying the distribution build |

## Build

```bash
./gradlew build          # Compile all modules
./gradlew runIde         # Launch IDE with plugin loaded (development)
./gradlew test           # Run all tests
./gradlew :plugins:hyve-knowledge:buildPlugin   # Build knowledge plugin only
```

## Architecture

Three-layer split enforces dependency isolation:

- **Core** (`plugins/hyve-knowledge/core/`): Pure Kotlin, no IntelliJ APIs. Owns all knowledge logic: indexers, HNSW vector search, SQLite DB, embedding cache, config. Usable by both IDE and MCP server.
- **IDE Plugin** (`plugins/hyve-knowledge/src/`): Bridges core with IntelliJ services. All IntelliJ-specific code lives here via `*Factory` and `*Bridge` classes. Never import IntelliJ APIs in core.
- **MCP Server** (`plugins/hyve-knowledge/mcp-server/`): Standalone process. Reads config from `~/.hyve/knowledge/config.json` with env var overrides via `McpConfig`. Hot-swaps active version via `ConfigFileWatcher` (5s mtime polling).

Key invariants:
- HNSW `chunk_index` maps 1:1 to vector ordinals — never mix versions in one index.
- Embedding is purpose-based: CODE models for Java decompiled sources, TEXT models for gamedata/docs/UI.
- Per-version knowledge directories at `~/.hyve/knowledge/versions/{slug}/` with independent DB + HNSW.
- Shared embedding cache at `~/.hyve/knowledge/embedding-cache.db` (SHA-256 content hash + model_id → vector).

## Compose UI

Uses Jewel + HyveTheme. Access colors via `HyveThemeColors.colors.*` — requires Composable context. Do not access color palette outside Composable scope.

## Testing

- **JUnit 4**: The IntelliJ platform test runner uses JUnit 4. Use `org.junit.Test`, not `org.junit.jupiter.api.Test`. Use `@Before`/`@After` (not `@BeforeEach`/`@AfterEach`). JUnit 5 `@TempDir` is unavailable — use `Files.createTempDirectory()` with manual cleanup. The MCP server module (`mcp-server/`) uses JUnit 5 independently.

## Conventions

- **TDD**: Write failing tests first. The superpowers `test-driven-development` skill enforces RED-GREEN-REFACTOR. Always use it for new features. Do not write implementation code before tests exist.
- **Comments**: Use timeless present tense (see `~/.claude/conventions/temporal.md`). Never reference the change process — describe what the code IS, not what was done to it.
- **Documentation**: CLAUDE.md = navigation index only. Architecture rationale → README.md. See `~/.claude/conventions/documentation.md`.
- **Workflow**: Use superpowers `brainstorming` before implementing new features. Use `writing-plans` after design approval. When brainstorming proposes an approach involving data models, protocols, external APIs, or storage formats → invoke `decision-critic` before proceeding to writing-plans.
