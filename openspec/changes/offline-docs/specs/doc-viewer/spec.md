# Doc Viewer Specification

## Purpose

Provide an in-IDE browsable documentation experience via a tool window navigation tree and read-only markdown editor tabs, mirroring the HytaleModding website layout.

## Behaviors

### Tool Window — Navigation Tree

#### WHEN the user opens "Hytale Documentation" from the Tool Windows dropdown
THEN a right-anchored tool window opens
AND displays a search bar at the top
AND a tree navigation below, organized by categories matching the website sidebar
AND a language toggle dropdown in the header
AND a "View on GitHub" button in the toolbar

#### WHEN docs have not been synced yet
THEN the tree area shows: "Documentation not yet synced"
AND a "Sync Now" button below the message

#### WHEN the user clicks a doc entry in the tree
THEN the corresponding `.md` file opens as a read-only editor tab with markdown preview

#### WHEN the user types in the search bar
THEN a full-text search runs across all cached `.md` files
AND results appear as a flat list replacing the tree temporarily
AND each result shows: title, snippet with highlighted match, relative path
AND clicking a result opens the doc in an editor tab

#### WHEN the user clears the search bar
THEN the tree navigation returns to its normal state

#### WHEN the user selects a different language from the dropdown
THEN if the locale is already cached, the tree rebuilds from that locale's `nav-tree.json`
THEN if the locale is not cached, a sync is triggered for that language
AND the tree shows a loading indicator until sync completes

#### WHEN the user clicks "View on GitHub"
THEN the browser opens `https://github.com/{repo}/blob/{branch}/content/docs/{locale}/{relativePath}`

### Editor Tab — Markdown Preview

#### WHEN a doc is opened from the tree or RAG citation
THEN it opens in a new editor tab titled with the doc's frontmatter title (or filename)
AND the tab shows IntelliJ's built-in markdown preview (rendered HTML)
AND the tab is read-only (no editing)
AND a thin info banner appears at the top: doc title + "View on GitHub" link

#### WHEN a link in the rendered doc points to another doc in the cache
THEN clicking it opens that doc in a new editor tab (not a browser)

#### WHEN a link points to an external URL
THEN clicking it opens the default browser

#### WHEN the doc contains images with local paths
THEN images render from the local cache directory

#### WHEN the IDE theme changes (light ↔ dark)
THEN the markdown preview re-renders with updated theme colors (automatic via IntelliJ markdown plugin)

## Inputs

- **Nav tree source**: `~/.hyve/knowledge/docs-offline/{locale}/nav-tree.json`
- **Doc files**: `~/.hyve/knowledge/docs-offline/{locale}/**/*.md`
- **Search query**: User-typed text in the search bar

## Outputs

- **Tree UI**: Categorized, collapsible tree with section headers and doc entries
- **Search results**: Flat list of matching docs with title, snippet, path
- **Editor tabs**: Read-only markdown-rendered tabs

## Navigation Tree Structure

Derived from `meta.json` files. Example for English:

```
Introduction
Quick Start
Established Information/
  ├── FAQ
  ├── Developer QA Insights
  ├── Client/
  │   └── ...
  ├── Server/
  │   └── ...
  └── Gameplay/
      └── ...
── Official Documentation ──
Custom UI/
  ├── Markup
  ├── Layout
  ├── Common Styling
  └── Type Documentation/
      └── ...
NPC Documentation
NPC/
  └── ...
World Generation/
  └── ...
── Guides ──
Learning to Learn
Java Basics/
  └── ...
Server Plugins/
  └── ...
Entity Component System/
  └── ...
Prefabs
── Documentation ──
ArgTypes
Entities
Events
Interactions
Sounds
── Community ──
Publishing Your Mod/
  └── ...
Contributing/
  └── ...
```

Section separators (`---Name---` in `meta.json`) render as non-clickable category headers with bold text and extra top padding.

## Edge Cases

### `meta.json` missing for a directory
- Fall back to alphabetical listing of `.md` files in that directory
- Use filename (sans extension, dashes replaced with spaces, title-cased) as display name

### Doc has no frontmatter title
- Use the filename as the tab title

### Search returns no results
- Show: "No results for '{query}'"

### Cache directory doesn't exist
- Tree shows empty state with "Sync Now" button

### Very long doc titles
- Truncate in tree with ellipsis at ~50 characters
- Full title shown as tooltip on hover

### Multiple docs open simultaneously
- Standard IntelliJ tab behavior — each doc gets its own tab, user can split/rearrange

## Constraints

- **Tool window startup**: Tree must render immediately from cached `nav-tree.json` — no network calls on tool window open
- **Search performance**: Full-text search across ~224 docs should return results in <200ms. Use an in-memory index built on first tool window open.
- **Markdown preview**: Depends on IntelliJ's bundled Markdown plugin. If absent (unlikely in IDEA), show a notification suggesting they enable it, and fall back to plain text display.
- **Theme compliance**: All custom CSS must use IntelliJ's CSS variables (`--jb-*`) to match any theme, not just dark.

## Acceptance Criteria

1. Tool window appears in Tool Windows dropdown as "Hytale Documentation"
2. Tree matches the website's sidebar structure and ordering
3. Clicking a doc opens a rendered, read-only markdown tab
4. Search finds docs by content with <200ms response time
5. Language toggle switches the displayed docs and rebuilds the tree
6. "View on GitHub" opens the correct source URL
7. Inter-doc links navigate within the IDE, not to a browser
8. Empty state shows "Sync Now" when no cache exists
9. Themes: preview looks correct in both light and dark IDE themes

## Test Coverage

- **Unit**: `meta.json` parser → `DocNavTree` model, search indexing, path resolution for inter-doc links
- **UI**: Manual verification of tree rendering, search interaction, editor tab appearance, theme switching
