# Auto-Decompilation During Indexing

## Problem

When `BuildAllIndexAction` or `SiblingIndexTask` runs, code indexing is skipped if decompiled source files don't exist. Users must manually run "Decompile Hytale Server" first. This causes pre-release and sibling patchline builds to report "Code: skipped (no decompiled source)" since nobody ran the separate decompile step.

## Solution

Extract decompilation logic into a stateless `DecompileService`. Both the manual decompile action and the auto-indexing pipeline call it. Track JAR staleness via a `decompile_meta.json` file containing the server JAR's SHA-256 hash.

## Design

### New: `DecompileService`

Location: `plugins/hyve-knowledge/src/com/hyve/knowledge/decompile/DecompileService.kt`

Stateless object with three methods:

- `decompile(serverJar: File, outputDir: File, indicator: ProgressIndicator)` — FernFlower decompilation + post-processing fixes. Extracted from `DecompileTask.run()`.
- `isStale(serverJar: File, outputDir: File): Boolean` — reads `decompile_meta.json`, compares stored JAR SHA-256. Returns `true` if no meta, no files, or hash mismatch.
- `writeDecompileMeta(serverJar: File, outputDir: File)` — writes `decompile_meta.json` after successful decompilation.

### Refactored: `DecompileTask`

Thin wrapper around `DecompileService`. Keeps `onSuccess`/`onThrowable` notification logic. `run()` delegates to `DecompileService.decompile()` + `writeDecompileMeta()`.

### Modified: `BuildAllIndexAction.BuildAllTask`

Before code indexing (currently lines 96-110), insert:

```kotlin
val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
val decompileDir = settings.resolvedDecompilePath()
if (serverJar != null && serverJar.exists() && DecompileService.isStale(serverJar, decompileDir)) {
    indicator.text = "Decompiling server code..."
    try {
        DecompileService.decompile(serverJar, decompileDir, indicator)
        DecompileService.writeDecompileMeta(serverJar, decompileDir)
    } catch (e: Exception) {
        log.warn("Auto-decompilation failed", e)
        results.add("Code: decompilation failed (${e.message})")
        // Continue to other corpora
    }
}
```

On failure, log and continue — Game Data, Client UI, Docs still index.

### Modified: `SiblingIndexTask`

Same auto-decompile pattern. Install path is already swapped, so `HytaleInstallPath.serverJarPath()` returns the sibling's JAR. Output goes to `~/.hyve/knowledge/versions/{siblingSlug}/decompiled/`.

### `decompile_meta.json`

```json
{
  "jarHash": "a1b2c3...",
  "decompiledAt": "2026-03-01T20:30:00Z"
}
```

Stored at `{decompileDir}/decompile_meta.json`. SHA-256 of the full server JAR.

### UX

- Silent with progress: decompilation updates the existing background task progress indicator text. No extra dialogs or notifications.
- Progress fraction stays at 0.0 during decompilation (pre-code-indexing), then the 0-25% code indexing phase follows.

### Error handling

- Decompilation failure: caught, logged, reported as "Code: decompilation failed (message)". Other corpora continue.
- Missing server JAR: no decompilation attempted, falls through to existing "skipped (no decompiled source)" path.

## Decisions

- **Staleness detection**: SHA-256 hash of server JAR stored in `decompile_meta.json`. Re-decompile on hash mismatch.
- **No confirmation dialog**: auto-decompilation runs silently within the indexing task.
- **Approach B (DecompileService)**: clean extraction, both manual action and auto-indexing share the same code path.
