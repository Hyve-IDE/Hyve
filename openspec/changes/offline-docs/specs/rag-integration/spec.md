# RAG Integration Specification

## Purpose

Connect the existing Hytale Knowledge RAG search to the offline docs viewer, so that clicking a DOCS corpus search result opens the full doc in an editor tab rather than just showing a snippet.

## Behaviors

### WHEN a user clicks a DOCS corpus search result AND the offline doc exists locally
THEN the corresponding `.md` file opens in a read-only editor tab with markdown preview
AND the tab scrolls to or highlights the relevant section if the result has a section anchor

### WHEN a user clicks a DOCS corpus search result AND the offline doc does NOT exist locally
THEN the existing behavior is preserved (show snippet, no editor tab)
AND a subtle hint is shown: "Sync documentation for full doc viewing"

### WHEN a user right-clicks a DOCS corpus search result
THEN the context menu includes "Open Full Document" as an option
AND the option is grayed out with "(not synced)" suffix if the offline doc doesn't exist

### WHEN the RAG returns a result with `filePath` pointing to the RAG cache (`~/.hyve/knowledge/docs/`)
THEN the path resolver maps it to the offline docs path (`~/.hyve/knowledge/docs-offline/{locale}/`)
AND the mapping uses `relativePath` from the search result (e.g., `guides/plugin/creating-commands.mdx` → `guides/plugin/creating-commands.md`)

## Inputs

- **`SearchResult`** from `KnowledgeSearchService` with:
  - `corpus = DOCS`
  - `filePath` — path to the RAG-cached chunk file
  - `relativePath` — e.g., `guides/plugin/creating-commands.mdx`
  - `displayName` — doc title

## Outputs

- **Editor tab**: Opens the offline doc `.md` file in a read-only markdown preview tab
- **Fallback**: Existing click behavior when offline doc is unavailable

## Path Resolution

```
RAG SearchResult
  relativePath: "guides/plugin/creating-commands.mdx"

  ↓ resolve()

Offline doc path:
  ~/.hyve/knowledge/docs-offline/{locale}/guides/plugin/creating-commands.md
```

Rules:
1. Take `relativePath` from the `SearchResult`
2. Replace `.mdx` extension with `.md`
3. Prepend `~/.hyve/knowledge/docs-offline/{currentLocale}/`
4. Check if file exists; if not, return null (fallback to existing behavior)

## Edge Cases

### RAG result has no `relativePath`
- Fall back to existing click behavior (no offline doc navigation)

### Locale mismatch — RAG indexed English, user switched to German
- Attempt to find the doc in the current locale first
- If not found, fall back to English locale
- If neither exists, fall back to existing behavior

### Doc was deleted from repo but RAG index still references it
- File won't exist at offline path
- Fall back to existing behavior
- RAG re-index will eventually clean up stale entries

### Multiple RAG results from the same doc (different sections)
- Each click opens the same editor tab (or focuses it if already open)
- Future enhancement: scroll to the relevant section via heading anchor

## Constraints

- **Non-breaking**: The integration must not change behavior for CODE, CLIENT, or GAMEDATA corpus results — only DOCS
- **Graceful degradation**: If the offline docs feature is disabled or docs are not synced, RAG results behave exactly as they do today
- **No new dependencies**: The resolver is a simple path mapping function, no new libraries needed

## Acceptance Criteria

1. Click a DOCS search result → offline doc opens in editor tab
2. Click a CODE/CLIENT/GAMEDATA result → existing behavior unchanged
3. Docs not synced → click behaves as before, no errors
4. Right-click "Open Full Document" works and respects sync state
5. Path resolution correctly maps `.mdx` → `.md` and handles locale

## Test Coverage

- **Unit**: Path resolver mapping (`.mdx` → `.md`, locale prefix, missing file fallback)
- **Integration**: End-to-end click flow with mocked search results and cached docs
