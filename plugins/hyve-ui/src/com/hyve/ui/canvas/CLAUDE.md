# CLAUDE.md

## Overview

Canvas rendering and interaction state for visual editor, with drag/resize operations and element bounds calculation.

## Index

| File | Contents (WHAT) | Read When (WHEN) |
|------|-----------------|------------------|
| `CanvasState.kt` | Drag state (descendant IDs, preview anchor), resize state, bounds calculation, element selection/movement | Implementing drag feedback, layout changes, selection logic |
| `CanvasView.kt` | Compose rendering of canvas (painter calls, input handlers) | Modifying visual rendering, adding gestures |
| `ScreenshotMode.kt` | Screenshot capture configuration | Taking canvas screenshots for testing |
| `ScreenshotReferencePanel.kt` | Reference panel for screenshot comparison | Visual regression testing |
