# Tasks for Offline Hytale Documentation

## 1. Doc Sync Engine

- [ ] 1.1 Create `DocsSyncService` — application-level service managing sync state, last-sync timestamp, and hash manifest for incremental updates
- [ ] 1.2 Implement incremental fetch — compare GitHub tree SHA against local manifest to identify added/changed/deleted files; only download diffs
- [ ] 1.3 Implement MDX-to-MD converter — richer than existing `stripMdx()`: convert `<Callout type="X">` to `> **X:** ...` blockquotes, convert `<OfficialDocumentationNotice />` to a standard notice paragraph, strip imports, preserve all standard markdown
- [ ] 1.4 Fetch and parse `meta.json` files — download all `meta.json` in the docs tree, parse into a `DocNavTree` model with ordering, section separators, titles, and icons
- [ ] 1.5 Write converted `.md` files to `~/.hyve/knowledge/docs-offline/{locale}/` preserving directory structure
- [ ] 1.6 Download referenced images — scan converted markdown for image references, fetch and cache images locally, rewrite image paths to local cache
- [ ] 1.7 Create `DocsSyncStartupActivity` — `ProjectActivity` that runs on IDE startup, checks `syncOfflineDocsOnStart` setting, triggers background sync if enabled
- [ ] 1.8 Add progress notification — background task shows progress via IntelliJ's `BackgroundableTask` with "Syncing Hytale Documentation..." message and file count

## 2. Settings Integration

- [ ] 2.1 Add `syncOfflineDocsOnStart: Boolean = true` to `KnowledgeSettings.State`
- [ ] 2.2 Add toggle to `GeneralConfigurable` settings panel — "Sync offline documentation on startup"
- [ ] 2.3 Add manual "Sync Documentation Now" action to the tool window gear menu and the Hytale Knowledge gear menu

## 3. Navigation Tree (Tool Window)

- [ ] 3.1 Register `HytaleDocsToolWindowFactory` in `hyve-knowledge.xml` — anchor right, icon matching docs theme
- [ ] 3.2 Build `DocNavTree` model from parsed `meta.json` files — tree nodes with title, icon hint, children, section separators, and associated `.md` file path
- [ ] 3.3 Implement Compose-based tree UI — collapsible categories, section headers (from `---Section Name---` entries in meta.json), doc entries; matches IntelliJ tree conventions
- [ ] 3.4 Implement search bar — full-text search across all cached `.md` files; results displayed as flat list with title + snippet + path; clicking opens the doc
- [ ] 3.5 Add language toggle dropdown in tool window header — populated from available locale directories in cache; switching language re-syncs if needed and rebuilds the tree
- [ ] 3.6 Add "View on GitHub" button in tool window toolbar — opens current doc's source URL in the default browser
- [ ] 3.7 Handle empty state — "Documentation not yet synced" message with a "Sync Now" button when no local cache exists

## 4. Editor Tab (Markdown Preview)

- [ ] 4.1 Create `OfflineDocEditorProvider` — `FileEditorProvider` that accepts files under the `docs-offline/` cache directory, opens them as read-only with IntelliJ's markdown preview
- [ ] 4.2 Add custom CSS stylesheet — style `<blockquote>` callouts (warning = amber border, info = blue border), match IDE theme colors; inject via markdown preview extension point
- [ ] 4.3 Add read-only banner — thin bar at top of editor tab: "Hytale Documentation — Read Only" with GitHub link button
- [ ] 4.4 Ensure local image rendering — configure markdown preview to resolve relative image paths against the local cache directory
- [ ] 4.5 Handle inter-doc links — intercept markdown link clicks; if link points to another doc in the cache, open it in a new editor tab instead of launching a browser

## 5. RAG Integration

- [ ] 5.1 Map RAG `SearchResult.filePath` (DOCS corpus) to offline doc path — create a resolver that maps the RAG's cached chunk path to the corresponding `docs-offline/` markdown file
- [ ] 5.2 Modify `SearchResultCard` click handler — when corpus is DOCS and offline doc exists, open the offline doc editor tab; fall back to existing behavior if not cached
- [ ] 5.3 Add "Open Full Doc" action to result card context — right-click option on DOCS results to explicitly open in the docs viewer

## 6. Verification

- [ ] 6.1 Manual testing — fresh install flow (no cache), incremental sync (change 1 file), language switch, search, tree navigation, editor tab rendering, RAG click-through
- [ ] 6.2 Test with sync disabled — verify no disk usage, no startup delay, tool window shows empty state
- [ ] 6.3 Test offline behavior — disconnect network after initial sync, verify all docs still accessible
- [ ] 6.4 Test rate limiting — verify graceful handling when GitHub API rate limit is hit mid-sync
- [ ] 6.5 Unit tests for MDX converter — `<Callout>` conversion, `<OfficialDocumentationNotice />` removal, import stripping, nested JSX, edge cases
- [ ] 6.6 Unit tests for `meta.json` parser — ordering, section separators, missing meta.json fallback, malformed JSON
- [ ] 6.7 Unit tests for incremental sync — hash comparison, add/change/delete detection
