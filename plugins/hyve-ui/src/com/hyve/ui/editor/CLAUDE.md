# CLAUDE.md

## Overview

Editor providers and save handlers for .ui file editing with text/visual toggle.

## Index

| File | Contents (WHAT) | Read When (WHEN) |
|------|-----------------|------------------|
| `HyveUIEditorProvider.kt` | Visual editor factory for .ui files, used as preview provider by TextEditorWithPreviewProvider | Creating new editor instances, modifying visual editor setup |
| `HyveUITextEditorWithPreviewProvider.kt` | Wrapper provider for text/visual toggle with SHOW_PREVIEW default layout | Changing editor toggle behavior, default layout mode |
| `HyveUIEditor.kt` | Main visual editor component with Compose canvas and property inspector | Modifying editor UI layout, canvas interactions |
| `HyveUIEditorContent.kt` | Editor content layout (canvas + inspector side panel) | Restructuring editor pane arrangement |
| `HyveUIEditorSaveHandler.kt` | Save hook that traverses TextEditorWithPreview wrapper to find HyveUIEditor | Fixing save failures, editor wrapper integration |
| `HyveUIEditorSaveHandler.kt` (part of undo/) | Undo manager and command recording | Implementing undo/redo operations |
