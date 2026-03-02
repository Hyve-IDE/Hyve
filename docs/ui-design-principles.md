# UI Design Principles for Vibe-Coded Software

Distilled from a video on redesigning AI-generated UIs into professional, usable products.

## Icons

- Never use emojis as UI icons. Use a proper icon library (Phosphor, Lucide, etc.).
- Icons should be professional and contextually appropriate.
- Small, meaningful icons add a splash of color and convey information density without clutter.

## Color

- Never let AI choose your colors. AI defaults to bright, clashing palettes.
- Use muted, cohesive color schemes. Subtle background shifts (e.g., dark blue to dark green) make a big difference.
- Add color through *data* (charts, status indicators) rather than decorative elements (gradient circles, colored buttons).

## Layout

- Never let AI choose your layout. AI produces repetitive, flat arrangements.
- Eliminate redundancy: if a KPI appears in the dashboard, don't repeat it on every page.
- Collapse secondary actions into menus (triple-dot, popovers) instead of showing every button.
- Align content to the left and tighten spacing. AI tends to over-pad.
- Use the space you have. If a form is sparse relative to available width, a modal or denser layout is more fitting.
- Two-column layouts elevate simple content. Tabs scale well as features grow.

## Information Hierarchy

- Increase the size of what users care about (e.g., cost per month). Decrease what they don't (e.g., plan name).
- Show users actionable context: what the next tier includes, what discount they're getting.
- Move metadata to secondary positions (center, right-aligned) — keep primary content (name, status) prominent on the left.

## Cards and Lists

- Collapse busy cards: tuck buttons into dot menus, move dates to center, replace text chips with icons.
- Every card element should earn its space. If a card doesn't do anything, remove it.
- "Vibe-coded" KPI cards (icon + number + label) are a red flag. Replace with micro-charts or richer data displays.

## Features and Interactions

- Don't forget low-hanging fruit: simple features that are genuinely useful (e.g., toggle to compare items, filter views).
- Collapse advanced options by default. Show the common path, let power users expand.
- Design layouts that allow new features to slot in easily (tabs, expandable sections).

## Data Visualization

- Replace generic bar charts with contextually rich alternatives (maps with shaded regions, donut charts, micro-charts).
- Charts should convey the *story* of the data, not just display numbers.
- Pair visualizations with supporting data tables or summaries.

## Landing Pages

- Landing pages are about *presentation*, not complexity. Better presentation = better conversion.
- Use real product screenshots (with slight perspective/skew) instead of placeholder icons or illustrations.
- Establish trust through visual quality. There is a subconscious standard users expect.

## General Anti-Patterns in AI-Generated UI

| Anti-Pattern | Fix |
|---|---|
| Emojis as icons | Professional icon library |
| Bright clashing colors | Muted, cohesive palette |
| Repeated KPIs across pages | Show data once, in context |
| Gradient profile circles | Account card or avatar |
| Every action visible as a button | Collapse into menus/popovers |
| Sparse forms in wide layouts | Use modals or denser layouts |
| Generic bar charts everywhere | Contextual visualizations |
| Decorative elements with no function | Remove or replace with data |
