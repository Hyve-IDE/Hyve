# Doc Sync Specification

## Purpose

Automatically sync Hytale modding documentation from the `HytaleModding/site` GitHub repository to a local cache, converting MDX files to clean Markdown for offline reading.

## Behaviors

### WHEN the IDE starts up AND `syncOfflineDocsOnStart` is enabled
THEN a background task fetches the GitHub tree for the configured branch
AND compares file SHAs against the local manifest
AND downloads only new or changed files
AND converts MDX to MD
AND updates the local manifest
AND shows progress via `BackgroundableTask` notification

### WHEN the IDE starts up AND `syncOfflineDocsOnStart` is disabled
THEN no sync occurs
AND no network requests are made
AND existing cached docs remain accessible

### WHEN the user manually triggers "Sync Documentation Now"
THEN a full sync runs regardless of the `syncOfflineDocsOnStart` setting
AND follows the same incremental flow as startup sync

### WHEN the GitHub API returns a rate limit error (HTTP 403/429)
THEN the sync logs a warning
AND falls back to the existing local cache
AND notifies the user: "GitHub rate limit reached. Using cached docs."

### WHEN a file exists locally but was deleted from the repo
THEN the local file and its manifest entry are removed on next sync

### WHEN the user changes the docs language in settings
THEN the next sync fetches docs for the new locale
AND the tree navigation rebuilds from the new locale's `meta.json` files

## Inputs

- **GitHub repo**: `HytaleModding/site` (configurable via `docsGithubRepo` setting)
- **Branch**: `main` (configurable via `docsGithubBranch` setting)
- **Locale**: `en` (configurable via `docsLanguage` setting)
- **File filter**: `content/docs/{locale}/**/*.{md,mdx}` plus `**/meta.json`

## Outputs

- **Converted markdown files**: `~/.hyve/knowledge/docs-offline/{locale}/` preserving directory structure, `.mdx` → `.md` extension
- **Cached images**: `~/.hyve/knowledge/docs-offline/{locale}/_images/` with paths rewritten in markdown
- **Manifest file**: `~/.hyve/knowledge/docs-offline/manifest.json` — maps relative paths to SHA hashes and last-modified timestamps
- **Nav metadata**: `~/.hyve/knowledge/docs-offline/{locale}/nav-tree.json` — pre-parsed navigation tree from `meta.json` files

## MDX-to-MD Conversion Rules

| MDX Syntax | Markdown Output |
|---|---|
| `import ... from '...'` | Removed |
| `<Callout type="warning">content</Callout>` | `> **Warning:** content` |
| `<Callout type="info">content</Callout>` | `> **Info:** content` |
| `<Callout type="tip">content</Callout>` | `> **Tip:** content` |
| `<OfficialDocumentationNotice />` | `> *This is official Hytale documentation.*` |
| Other self-closing JSX `<Component />` | Removed |
| Other JSX open/close `<Comp>...</Comp>` | Content preserved, tags removed |
| Frontmatter `---...---` | Preserved (used for title/description extraction) |
| `{variable}` JSX expressions | Removed |
| 3+ consecutive blank lines | Collapsed to 2 |

## Edge Cases

### Empty or malformed `meta.json`
- Fall back to alphabetical file listing for that directory
- Log a warning but don't fail the sync

### File with only frontmatter, no body
- Create the `.md` file with just the title as an H1

### Non-UTF-8 content
- Skip the file, log an error, continue with remaining files

### Concurrent sync requests (startup + manual trigger)
- Guard with a mutex/AtomicBoolean — if sync is already running, the second request is a no-op with a notification: "Documentation sync already in progress."

### First sync with no cache
- Full download of all docs for the selected locale (~224 files for English)
- Show progress: "Syncing Hytale Documentation... (42/224)"

### Image references pointing to external URLs
- Leave external URLs as-is (don't download third-party images)
- Only cache images hosted within the `HytaleModding/site` repo

## Constraints

- **Startup impact**: Sync must run on a background thread — never block the EDT or delay IDE startup
- **Disk usage**: English docs are ~2-5 MB total; with all 26 languages it could be ~50-100 MB. Only sync the selected language by default.
- **Network**: Respect GitHub API rate limits (60 req/hour unauthenticated). The tree API is 1 request; raw file downloads don't count against the API rate limit (they hit `raw.githubusercontent.com`).
- **Atomicity**: Write new files to a temp directory first, then swap into the cache dir on success — prevents partial/corrupt state on interrupted sync.

## Acceptance Criteria

1. Fresh install: open IDE → docs sync in background → tool window shows full nav tree within ~30s on reasonable internet
2. Subsequent launches: sync completes in <5s when no docs have changed
3. Toggle disabled: no network activity on startup, no disk writes
4. Language switch: changing from `en` to `de-DE` in settings triggers sync for German docs
5. Rate limit: graceful fallback with user notification, cached docs remain usable
6. Interrupted sync: no corrupt files left in cache

## Test Coverage

- **Unit**: MDX converter (all rules in table above), frontmatter extraction, SHA comparison logic, `meta.json` parser
- **Integration**: Full sync cycle against a mock GitHub API (or fixture files), incremental update detection, manifest read/write
