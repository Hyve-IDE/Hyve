# Visual Editor Architecture

## Drag Operation Design

### Descendant Tracking via derivedStateOf

During a drag operation, children and nested descendants must visually move with their parent without waiting for `commitDrag()`. The solution pre-computes a set of descendant IDs once per drag-start or selection change, rather than walking the tree on every `getBounds()` call.

**Trade-off:** O(N) memory for descendant set vs. O(1) per-frame lookup. Acceptable because element counts are small (typically <200 per UI file).

**Invariant:** `derivedStateOf` only recomputes when states read inside the lambda change. The descendant set reads `_isDragging` and `_selectedElements` but not `_dragOffset`, so descendants remain stable during a drag session—they only recompute when drag starts/ends or selection changes.

### dragPreviewAnchor: Single-Select Only

The anchor inspector must show live-updating values during drag without committing to the file. A separate `dragPreviewAnchor` state tracks the computed anchor value for the selected element during drag.

**Limitation:** Multi-select drag returns `null` (no preview) because:
1. The inspector displays a single anchor value (L/T/R/W/H/B) — no UI exists to show per-element previews simultaneously
2. Extending to multi-select would require a new aggregation UI (mixed values or per-element rows), which is out of scope
3. The constraint is satisfied for the common single-select case

**Invariant:** Preview values are derived from `calculateMovedAnchor()` (pure math, no side effects) and only displayed; onValueChange callbacks do not fire during preview.

## Editor Toggle Architecture

### TextEditorWithPreviewProvider Wrapper

The visual editor is wrapped in IntelliJ's `TextEditorWithPreviewProvider` pattern, which provides:
- Free 3-mode toolbar (text-only, split, visual-only)
- State persistence across IDE restarts
- Platform integration and layout management

Alternative (custom SplitEditor) would duplicate this functionality with more maintenance burden.

### Default Layout: SHOW_PREVIEW

By default, opening a .ui file shows the visual editor only (not a split view). This is achieved by overriding `createSplitEditor()` to pass `Layout.SHOW_PREVIEW` instead of the platform default `Layout.SHOW_EDITOR_AND_PREVIEW`.

### Editor Type ID Change

The old provider used type ID `'hyve-ui-visual-editor'`; the new wrapper uses `'hyve-ui-split-editor'`. IntelliJ persists editor tab state keyed by type ID, so users will lose their previously-open .ui tabs on upgrade (one-time event). This is acceptable for a pre-release editor; implementing a migration adds complexity for a transient problem.

### Save Handler: Wrapper Traversal

The `HyveUIEditorSaveHandler` hooks `beforeAllDocumentsSaving` and iterates `FileEditorManager.allEditors` looking for `HyveUIEditor` instances. After wrapping in `TextEditorWithPreview`, the direct instanceof check fails silently. The fix checks both:
1. Direct `HyveUIEditor` instance
2. `TextEditorWithPreview.previewEditor` property (containing the wrapped HyveUIEditor)

This is correctness-critical: Ctrl+S must save visual editor changes, not just text changes.

## Rendering Call Frequency

`getBounds()` is called per-element per-frame during rendering. Any new checks added here must be O(1), not O(N) tree walks. Both the descendant set lookup (Set.contains) and the anchor preview computation (pure math) meet this requirement.
