# NPC Node Editor — Pre-Design Question Inventory

> **Purpose:** This document enumerates every question that must be answered before designing
> a node-based NPC editor for Hytale within the Hyve IDE. It is a **question set**, not a
> design document. The questions are organized by dependency — foundational questions that
> gate all subsequent decisions come first.
>
> **How to use this:** Work through Tier 1 first. Many Tier 2 and Tier 3 questions cannot
> be meaningfully answered until Tier 1 answers exist. Within each category, questions are
> ordered from most foundational to most dependent.

---

## Data Model Summary (Verified)

This section documents the actual Hytale NPC system structure as confirmed by official
Hytale documentation (`npc-doc.mdx`). It supersedes any speculative assumptions made
earlier in this document.

### Core Execution Model: Priority-Ordered Instruction List

The NPC AI system is NOT a flat state machine with separate States/Transitions arrays.
The actual structure is a **priority-ordered instruction list**. Each entry is an
`Instruction` object evaluated top-to-bottom; the first one whose Sensor matches executes.

```json
{
  "Sensor": { "Type": "...", ... },       // Condition check — omit to always match
  "Actions": [{ "Type": "...", ... }],    // Actions to execute on match
  "BodyMotion": { "Type": "...", ... },   // Body movement override
  "HeadMotion": { "Type": "...", ... },   // Head movement override
  "Instructions": [ ... ],               // Nested sub-instructions (hierarchical)
  "Continue": false,                      // If true, continue evaluating after match
  "ActionsBlocking": false,               // If true, wait for action completion
  "ActionsAtomic": false,                 // If true, only execute if ALL actions can run
  "TreeMode": false,                      // If true, enable behavior tree semantics
  "Weight": 1.0                           // For weighted-random instruction groups
}
```

### States Are Implicit — No Separate States Array

States are not a top-level array. They are expressed as Sensor checks:

```json
{ "Sensor": { "Type": "State", "State": "Idle" }, "Instructions": [ ... ] }
```

Substates use dot notation: `.Default`, `.Guard`, `.Chase`. State changes are Actions:

```json
{ "Type": "State", "State": "Combat" }
```

### The Role File IS the Behavior Definition

`Server/NPC/Roles/<RoleName>.json` contains everything — role attributes, `Parameters`,
`Instructions[]`, `StateTransitionController`, and `MotionControllerList`. There is no
separate "AI file" that a Role links to.

### DecisionMaking Files Are Components and Conditions

`Server/NPC/DecisionMaking/` contains:
- **Conditions** — reusable sensor definitions (e.g., `Linear_HP_Condition.json` with
  `{ "Type": "OwnStatPercent" }`)
- **Components** — reusable instruction fragments with
  `{ "Type": "Component", "Class": "Instruction", "Parameters": {...}, "Content": {...} }`
  Referenced via `{ "Reference": "ComponentName", "Modify": {...} }` with support for
  `_ImportStates` / `_ExportStates` for parameterized state references.

### Hybrid Paradigm: Priority List + Behavior Tree

The system supports both paradigms simultaneously. `TreeMode: true` on an `Instructions`
block enables behavior tree semantics (continue if children fail). `InvertTreeModeResult`
inverts the result. It is a hybrid, not a pure FSM or pure BT.

### StateTransitions Are a Separate Effect System

`StateTransitionController` entries fire side-effect Actions (animations, beacons) when
the NPC transitions BETWEEN states. Schema:
```json
{ "States": [{ "From": [...], "To": [...], "Priority": 0 }], "Actions": [...] }
```
They are NOT the mechanism for state transition logic — that lives in Instructions.

### Role Attribute Schema (Key Fields)

| Field | Type | Notes |
|---|---|---|
| `MaxHealth` | Integer or `{Compute: "..."}` | Required, Computable |
| `Appearance` | Asset | Required, Computable |
| `StartState` | String | Optional, default `"start"` |
| `DefaultSubState` | String | Optional, default `"Default"` |
| `DropList` | Asset | Optional, Computable |
| `NameTranslationKey` | String | Required, Computable |
| `DefaultPlayerAttitude` | String | Attitude enum value |
| `DefaultNPCAttitude` | String | Attitude enum value |
| `MotionControllerList` | Array of typed objects | e.g. `{Type: "Walk", Speed: 2.0}` |
| `Parameters` | Open key-value map | Each entry: `{Value, Description}` |
| `Instructions` | Array of Instruction objects | The behavior definition |

`{Compute: "expression"}` supports actual math expressions including division and
multiplication, e.g. `"ViewRange / DistractedPenalty"`, `"AlertedRange * 2"`.

### Builder Type Registry (183 Types, Fully Documented)

`npc-doc.mdx` documents all 183 builder types with attributes, types, constraints, and
stability status:

| Category | Example Types |
|---|---|
| Sensor | `State`, `And`, `Or`, `Not`, `Player`, `Mob`, `Target`, `Damage`, `Timer`, `Alarm`, `Flag`, `Block` |
| Action | `State`, `Attack`, `Spawn`, `Die`, `SetFlag`, `Beacon`, `PlayAnimation`, `Timeout`, `Sequence`, `Random` |
| BodyMotion | `Seek`, `Flee`, `Wander`, `Path`, `Nothing`, `Sequence`, `Timer` |
| HeadMotion | `Watch`, `Aim`, `Observe`, `Nothing`, `Sequence`, `Timer` |
| MotionController | `Walk`, `Fly`, `Dive`, `Swim` |
| IEntityFilter | `Attitude`, `LineOfSight`, `ViewSector`, `NPCGroup` |
| Instruction variants | `Instruction`, `Reference`, `Random` |

### NPC Groups

NPCGroup schema: `{ IncludeRoles: [], ExcludeRoles: [], IncludeGroups: [], ExcludeGroups: [] }`.
Groups implement `TagSet` and are purely classification — no spawn logic.

### AttitudeGroups

Two types exist under `Server/NPC/Attitude/`:
- **`Attitude/Roles/`** — NPC-to-NPC: `{ Groups: { Hostile: ["GroupA"], Friendly: ["GroupB"] } }`.
  Attitude enum: `IGNORE`, `HOSTILE`, `NEUTRAL`, `FRIENDLY`, `REVERED`.
- **`Attitude/Items/`** — NPC-to-Item: `{ Attitudes: { Dislike: ["tag"], Like: ["tag"] } }`.
  Sentiment enum maps to Attitude: `Ignore`, `Dislike`, `Neutral`, `Like`, `Love`.

### Flocks

`Server/NPC/Flocks/` contains flock size configurations:
- **Default type**: `{ Size: [min, max], MaxGrowSize: 8, BlockedRoles: [] }`
- **Weighted type**: `{ Type: "Weighted", MinSize: 2, SizeWeights: [25, 75], MaxGrowSize: 8 }`

### Cross-File Reference Convention

All cross-file references use **bare filename stems** — no paths, no extensions. Resolved
via `AssetMap.getAsset(name)`. Component references form a **DAG** (engine validates no
cycles via `InternalReferenceResolver`).

### Engine Runtime Features

- **Hot-reload**: Enabled by default (`autoReload: true`). `AssetMonitor` watches directories.
- **Debug system**: 34 flags (`RoleDebugFlags`): Display (nameplate), Trace (logging),
  Visualization (in-world overlays). Set via `"Debug"` field or runtime `NPCCommand`.
- **Expression compiler**: Full arithmetic, comparison, logical, bitwise operators. Compiled
  and type-checked at load time. Invalid expressions = `RuntimeException`.
- **Validation**: Strict typed validation at load time. Unknown fields silently ignored.
  State setter/sensor consistency enforced. No reachability/deadlock analysis.

---

## Tier 1 — Answer Before Any Design Begins

These questions gate every subsequent decision. Answering Tier 2 or 3 questions without
resolving these first risks building on false assumptions.

---

### 1. Paradigm Justification

Before investing in a node editor, establish that it's the right tool for the job.

**1.1** For which specific NPC authoring tasks does a node graph add genuine value over
JSON text editing that cannot also be achieved with JSON schema autocomplete, inline
documentation, and go-to-definition for filename-stem references?

> The Godot VisualScript deprecation (removed in 4.0) demonstrates that a visual layer
> which merely replicates what text does — without adding spatial reasoning or relationship
> visibility — is worse than text in every measurable way.

**1.2** Is the primary value of the node editor for **writing** new NPC behavior, or for
**reading and understanding** existing NPC behavior?

> These are different designs. A writing tool optimizes for rapid graph construction
> (keyboard-driven node creation, auto-wiring). A reading tool optimizes for navigation
> (click-to-jump, search-by-type, highlight-all-transitions). One must be primary.

**1.3** Have we shipped improved text tooling first (JSON schema validation, go-to-definition
for cross-file references, Find Usages for NPC names) and measured whether it's sufficient?

> Before building the most complex tool, validate that simpler improvements don't eliminate
> the majority of modder pain. Improved text tooling is 10x cheaper to build and maintain.
> Its success or failure directly informs whether the node editor is truly needed.

**1.4** Are we designing for the modder who actually exists, or the modder we imagine?

> The imagined modder is a non-technical content designer intimidated by JSON. The actual
> modder population (given that JSON hand-editing is the current barrier to entry) is likely
> technically literate. Designing for the imagined modder may produce a tool that
> condescends to the actual modder.

---

### 2. Scope and Boundaries

Define what's in and what's out before anything else.

**2.1** Which of the NPC-related file types belong inside the node editor, and which remain
in text or form editors?

| File Type | Path | Structure | Node Candidate? |
|-----------|------|-----------|----------------|
| NPC Roles | `Server/NPC/Roles/` | Full behavior definition (stats, instructions, state machine) | **Strongest node candidate** — contains the Instructions[] behavior tree |
| NPC Groups | `Server/NPC/Groups/` | List (faction, members, spawn) | Form/list editor may suffice |
| DecisionMaking | `Server/NPC/DecisionMaking/` | Components and Conditions (reusable fragments) | Node candidate for Component editing; Conditions may suit form editor |
| Drop Tables | `Server/Drops/` | Weighted tree (containers → items) | Tree view may be better |
| Flocks | `Server/NPC/Flocks/` | Typed objects: `Default` (range size) or `Weighted` (weighted size), plus `MaxGrowSize`, `BlockedRoles` | Form editor — simple config, not behavioral |
| Attitude | `Server/NPC/Attitude/Roles/` | `{ Groups: { Hostile: [...], Friendly: [...] } }` mapping Attitude enum → NPCGroup names | Form editor — simple map structure |
| ItemAttitude | `Server/NPC/Attitude/Items/` | `{ Attitudes: { Like: [...], Dislike: [...] } }` mapping Sentiment → item tags | Form editor — simple map structure |

> **CORRECTED:** The previous table listed "NPC AI (DecisionMaking)" as the strongest node
> candidate and "NPC Roles" as merely config. This was wrong. The Role file IS the behavior
> definition — it contains `Instructions[]`, `StateTransitionController`, and
> `MotionControllerList`. DecisionMaking files are reusable Components and Conditions, not
> the primary behavior graph.

**2.2** Does the node editor manage a single file or a cross-file unified view?

> Opening a single DecisionMaking file and showing its state graph is one approach. Opening
> "an NPC" and showing nodes spanning Role + Group + AI + Drops as a unified canvas is
> another, far more complex approach. The unified view is more powerful but requires a
> virtual document model mapping onto multiple physical files, with per-node write routing,
> per-file dirty tracking, and cross-file undo. This is the single most consequential
> architectural decision.

**2.3** What is explicitly OUT of scope for the MVP?

> Without explicit exclusions, scope creep will delay shipping indefinitely. The MVP must
> draw a clear line: "This editor handles X. For Y, open the JSON file."

**2.4** How does the editor handle NPC data types it doesn't understand (Flocks, Attitude,
custom modder file types)?

> If the editor only handles Roles + Groups + Drops, what happens when a modder opens
> a Flock file? A graceful "not supported, opening as text" fallback must exist.

---

### 3. Data Model Verification

These are **research tasks** that must be completed by inspecting actual `Assets.zip` NPC
files, not inferred from parser code alone.

**3.1** What is the complete, verified field schema for each NPC-related file type?

> Current knowledge comes from `GameDataTextBuilder.kt` extraction code and test data, not
> from an authoritative schema. The following ambiguities must be resolved against actual
> game data:
>
> - **Parameters object**: Is the key set open (any string) or closed (fixed enum)?
>   If open, it cannot be represented with typed ports — requires a generic key-value editor.
> - **MotionControllerList entries**: Are they typed objects `{Type: "Walk", Speed: 2.0}` or
>   bare strings `"Walk"`? If typed with parameters, they need typed node representations.
> - **MaxHealth polymorphism**: Confirmed as `int` OR `{Compute: "..."}`. What other fields
>   support the `{Compute}` form? Each polymorphic field needs a special port widget.
> - **JSON key casing**: Mixed PascalCase and camelCase observed. What is canonical for write-back?
> - **JSON key ordering**: Is insertion order significant to the Hytale engine?
> - **JSONC tolerance**: Does the Hytale engine accept trailing commas, comments, BOM markers?

> **ANSWERED:**
> - `Parameters` is confirmed as an **open key-value map** with `{Value, Description}` per
>   entry. Also supports `TypeHint`, `Validate`, `Confine`, `Private` fields. Cannot be a
>   typed port dropdown — requires a generic key-value editor.
> - `MotionControllerList` entries are typed objects, e.g. `{Type: "Walk", Speed: 2.0}`.
>   They need typed node or form representations.
> - `{Compute: "..."}` is confirmed for `MaxHealth` and all fields marked "Computable" in
>   `npc-doc.mdx`. Expressions support a full operator set (see 5.5 below).
> - **JSON key casing: 100% PascalCase.** Every key in every NPC file starts uppercase. The
>   only exception is `"$Comment"`. No camelCase keys exist. The editor must write PascalCase.
> - **JSONC tolerance: None.** Hytale uses a custom strict JSON reader (`RawJsonReader`). No
>   `//` comments, no trailing commas, no JSON5 features. The `"$Comment"` convention uses a
>   normal JSON string field with a `$` prefix — the codec's `skipField()` ignores unknown keys.
> - **JSON key ordering:** Not confirmed as significant to the engine (the codec reads by key
>   name, not position), but the editor should preserve insertion order for clean git diffs.

**3.2** What is the exact internal schema of a DecisionMaking file?

> Known top-level arrays: `States[]`, `Transitions[{From, To, Condition}]`, `Actions[]`,
> `Goals[]`. Critical unknowns:
>
> - Are `States[]` entries bare strings or objects with sub-fields (entry actions, exit actions, timeout)?
> - Are `Actions[]` typed objects `{Type: "...", ...params}` or bare strings?
> - Is `Condition` on a Transition always a simple string identifier, an expression string,
>   a typed object `{Type: "...", Threshold: 0.25}`, or any of these?
> - Can conditions be composed (AND, OR, NOT)?

> **CORRECTED:** The premise of this question was wrong. `States[]`, `Transitions[]`,
> `Actions[]`, and `Goals[]` as top-level arrays in a "DecisionMaking file" do not exist in
> the Hytale engine's actual schema. `Server/NPC/DecisionMaking/` contains two kinds of
> files:
>
> 1. **Conditions** — reusable Sensor definitions (e.g. `{Type: "OwnStatPercent"}`).
> 2. **Components** — reusable instruction fragments with
>    `{Type: "Component", Class: "Instruction", Parameters: {...}, Content: {...}}`.
>
> There is no monolithic "DecisionMaking file" with a state machine inside it. The behavior
> graph lives in the Role file's `Instructions[]` array. Conditions and Components from
> `DecisionMaking/` are referenced inside Instructions via `{Reference: "ComponentName",
> Modify: {...}}`. The node editor schema must be redesigned around this structure.

**3.3** What is the execution semantics of `Goals[]`?

> Goals[] is structurally separate from States[] and Transitions[]. This could mean:
> (a) GOAP — a goal-oriented layer selecting states based on preconditions/effects;
> (b) a utility scoring layer assigning weights to states;
> (c) just a named list with no separate execution logic;
> (d) a completely different AI paradigm coexisting with the state machine.
> If (a) or (b), the node editor needs a separate topology for Goals — not just a list.

> **CORRECTED:** `Goals[]` was a fabrication arising from analysis of parser code, not from
> actual game files or documentation. The Hytale NPC system has no `Goals[]` concept. The
> execution model is a **priority-ordered `Instructions[]` list** with optional `Sensor`
> checks. GOAP-style goal selection does not exist in the documented system. All
> architecture assumptions premised on Goals[] are invalid and must be discarded.

**3.4** Is the state machine actually flat, or does it support sub-states, parallel regions,
or hierarchical nesting?

> The flat FSM assumption comes from parser code analysis, not from inspecting actual files.
> If any DecisionMaking file in Assets.zip contains nested States[] arrays or ParentState
> fields, the assumption is wrong and the entire visual paradigm must change. This must be
> verified empirically.

> **ANSWERED:** The system is hierarchical, not flat, on two axes:
>
> 1. **Nested Instructions**: Any `Instruction` can contain a child `Instructions[]` array,
>    allowing arbitrarily deep nesting. The priority-list evaluation applies recursively.
> 2. **Substates via dot notation**: States use dot notation for hierarchy (e.g.
>    `Combat.Default`, `Combat.Guard`, `Combat.Chase`). `DefaultSubState` on the Role
>    specifies which substate to enter by default when reaching a parent state.
>
> Additionally, `TreeMode: true` on an Instructions block switches its evaluation from
> priority-list to behavior tree semantics. This means the flat FSM assumption was entirely
> wrong — the system supports hierarchical nesting AND optional BT mode simultaneously.
> The visual paradigm must represent nested instruction trees, not a flat state diagram.

**3.5** What is the actual complexity distribution of NPC AI state machines in the base game?

> If the largest real NPC has 5 states and 8 transitions, the visual editor's value
> proposition is weak and performance concerns are irrelevant. If real NPCs reach 20+ states,
> the visual editor is justified and performance matters. This is an empirical question
> answerable by parsing Assets.zip.

> **ANSWERED:** The system uses a three-layer composition pattern: **Variant → Template →
> Components**. Complexity lives in templates, not in individual NPC files.
>
> - **Variant files** (e.g. `Trork_Warrior.json`) are typically **10-30 lines** — just a
>   `Reference` to a template plus parameter overrides (Appearance, Health, DropList).
> - **Template files** (e.g. `Template_Goblin_Ogre_Tutorial`) are **200-900+ lines** with
>   deep nesting (3-4 levels of Instructions). The ogre tutorial has **7 top-level states**
>   (Idle, Sleep, Eat, CallRat, Alerted, Combat, ReturnHome, Search), **~20+ parameters**,
>   **6 state transition rules**, and **~8 component references**.
> - **Component files** (e.g. `Component_Instruction_Intelligent_Chase`) are reusable
>   fragments of moderate complexity.
>
> This justifies the visual editor — template files are complex enough that a tree/graph view
> adds real value over scrolling through 900 lines of nested JSON. The Variant→Template
> layering also means the editor should support a "diff view" showing what a Variant overrides.

**3.6** Are there NPC AI paradigms beyond state machines at `Server/NPC/DecisionMaking/`?

> The PATH_RULES classify all files at that path as NPC_AI, but this is path-based — it
> doesn't mean all files share the same internal structure. Are there sub-directories or
> naming conventions distinguishing different AI paradigms (behavior trees, GOAP, utility)?

> **ANSWERED:** The system is a confirmed hybrid. The same `Instructions[]` framework
> supports both a priority-ordered list (default) and behavior tree semantics (`TreeMode:
> true`). `InvertTreeModeResult` further extends BT semantics. GOAP does not appear in the
> documented type registry. The `DecisionMaking/` path contains Components and Conditions,
> not separate AI paradigm files. The editor must represent a single unified paradigm — the
> priority-list / BT hybrid — rather than detecting and switching between separate paradigms.

---

### 4. Cross-File Reference Topology

These questions determine the edges in the NPC relationship graph.

**4.1** What field in a NPC Role file links it to its DecisionMaking file?

> The codebase extracts `aiType`/`AiType` from Role files. Is this the field that links to
> a specific DecisionMaking file by filename stem? Or is the link implicit (engine matches
> by naming convention)? Or does the entity's ECS `AiController` component specify it
> independently? This is the **most critical cross-file reference unknown**.

> **CORRECTED:** The question's premise was wrong. NPC Roles do not link to a separate
> "DecisionMaking file" at all. The Role file is the behavior definition — `Instructions[]`
> lives directly inside the Role. DecisionMaking files (Components and Conditions) are
> referenced from within Instructions via `{Reference: "ComponentName", Modify: {...}}`.
> The reference model is: Role → Component (via `Reference` field inside an Instruction),
> not Role → AI file. The `aiType`/`AiType` field extraction in the parser may be
> erroneous or may refer to a different engine concept that still needs investigation.

**4.2** What is the complete set of cross-file reference edges?

> **ANSWERED.** The complete cross-file reference graph is:
>
> **Role file outbound references (Asset fields):**
> | Field | Target |
> |---|---|
> | `Appearance` | Model asset |
> | `DropList` | ItemDropList file |
> | `OpaqueBlockSet` | BlockSet file (default: `"Opaque"`) |
> | `AttitudeGroup` | AttitudeGroup file |
> | `ItemAttitudeGroup` | ItemAttitudeGroup file |
> | `PossibleInventoryItems` | ItemDropList file |
> | `DeathInteraction` | Root Interaction file |
> | `HotbarItems[]` | Item assets |
> | `OffHandItems[]` | Item assets |
> | `Armor[]` | Item assets |
> | `DisableDamageGroups[]` | NPCGroup (TagSet) files |
> | `FlockSpawnTypes[]` | NPC Role names |
> | `FlockAllowedNPC[]` | NPC Role names |
> | `CombatConfig` | BalanceAsset file |
> | `InteractionVars.Parent` | Root Interaction file |
> | Variant `Reference` | Another NPC Role file (template) |
>
> **References inside Instructions (embedded in the tree):**
> | Pattern | Target |
> |---|---|
> | `{Reference: "ComponentName"}` | Component JSON file (DAG, no cycles) |
> | `Sensor.Reference` | Component Sensor file |
> | `PlayAnimation.Animation` | String ID resolved against the Model |
> | `PlaySound.SoundEventId` | Sound Event asset |
> | `Beacon.TargetGroups[]` | NPCGroup (TagSet) files |
> | `Spawn.Kind` | NPC Role name |
> | `Spawn.Flock` | FlockAsset file |
> | `TriggerSpawnBeacon.BeaconSpawn` | BeaconNPCSpawn config |
> | `Attack` | Root Interaction file |
> | `Role` (action) | NPC Role name (role switching) |
> | `Inventory.Item` | Item asset |
> | `Appearance` (action) | Model asset |
>
> **NPCGroup file:** `IncludeRoles[]` / `ExcludeRoles[]` → Role names;
> `IncludeGroups[]` / `ExcludeGroups[]` → other NPCGroup names.
>
> **AttitudeGroup file:** `Groups` → Map<Attitude enum, NPCGroup name arrays>.
>
> **FlockAsset file:** `BlockedRoles[]` → NPC Role names.
>
> **BeaconNPCSpawn file:** `NPCs[].Id` → NPC Role names.

**4.3** What is the reference key convention — always filename stems, or are there UUIDs,
full paths, or registry keys?

> **ANSWERED:** Always **bare filename stems** — no paths, no extensions, no prefixes. Every
> cross-file reference uses a string that matches a filename minus `.json`. Examples from
> actual game data: `"Appearance": "Bear_Grizzly"`, `"DropList": "Empty"`,
> `"Reference": "Template_Goblin_Ogre_Tutorial"`, `"AttitudeGroup": "Trork"`. The engine
> resolves these via `AssetMap.getAsset(name)` lookups keyed by filename stem. Validators
> like `FlockAssetExistsValidator` confirm: `getAssetMap().getAsset(name) != null`. No
> exceptions to this convention were found.

**4.4** Can NPC reference graphs contain cycles?

> **ANSWERED: No — the engine explicitly prevents them.** `InternalReferenceResolver.java`
> performs a recursive DFS cycle check (`validateNoCycles()`) on component references and
> throws `IllegalArgumentException("Cyclic reference detected")` if found. Components form
> a **DAG** (directed acyclic graph).
>
> Role↔Group relationships are structurally non-cyclic: Groups reference Roles via
> `IncludeRoles`, but Roles only reference Groups in filter/action contexts (runtime
> queries, not structural definitions). Variant chains (Variant→Variant→Template) are
> resolved iteratively with a `do/while` loop, not recursively, so they also cannot cycle.
>
> The graph layout algorithm can safely assume a DAG for component references. Cross-file
> references between Roles, Groups, and AttitudeGroups are bidirectional in usage but
> unidirectional in definition — no structural cycles possible.

**4.5** How does `StartState` (in Role file) reference a named state in the behavior graph?

> `StartState` is a string (default `"start"`) on the Role. Because states are implicit
> (expressed as Sensor checks rather than a named array), this reference is to a Sensor
> condition that matches `{Type: "State", State: "start"}`. The editor must resolve this
> without a named-state lookup table. How does the editor validate and navigate this
> reference, and how does it present the "entry point" of the instruction tree visually?

---

### 5. Engine Constraints

**5.1** Does the Hytale engine validate NPC JSON on load against a strict schema?

> **ANSWERED:** Yes — strict **typed validation**, but **unknown fields are silently
> ignored**. Each builder's `validate()` method checks:
> - Required fields are present
> - Types match (`requireString`, `requireObject`, `expectBooleanElement`)
> - Range constraints (e.g. `MaxHealth > 0`, `InventorySize` 0-36, `HotbarSize` 3-8)
> - State string format (`MainState.SubState` convention)
> - Cross-asset references resolve (missing balance files = hard error)
> - Animation references exist on the model (missing = WARNING, non-fatal)
>
> Unknown fields are skipped by the codec's `skipField()`. This means **editor-only
> metadata COULD be embedded** using `$`-prefixed keys (following the `"$Comment"`
> convention) without breaking the engine. However, the sidecar `.hyve-meta` approach
> is cleaner and avoids polluting game files.

**5.2** What happens when a cross-file reference cannot be resolved (dangling reference)?

> **ANSWERED:** Multi-level response depending on reference type:
> - **Missing balance files** → hard validation error, collected in `List<String> errors`,
>   likely prevents NPC from loading. (`BuilderCombatConfig.validate()` checks
>   `BalanceAsset.getAssetMap().getAsset(override) == null`)
> - **Missing animations** → WARNING log, NPC still loads. (`validateAnimation()` logs at
>   `Level.WARNING` but does not add to error list)
> - **Missing component references** → hard error during `InternalReferenceResolver` resolution
> - **Missing template for Variant** → the appendix explicitly warns: "you need this file to
>   exist, because template is referencing it"
>
> The editor should treat all dangling references as **errors** (red), with missing
> animations demoted to **warnings** (yellow) to match engine behavior.

**5.3** Are NPC file changes hot-reloadable, or do they require a server restart?

> **ANSWERED: Yes — full hot-reload, enabled by default.** `NPCPlugin.NPCConfig.isAutoReload()`
> returns `true` by default. The `AssetMonitor` system watches directories with a
> `PathWatcherThread`, debounces changes via `FileChangeTask`, and fires
> `AssetMonitorEvent` with lists of created/modified/removed files. `BuilderRole` and
> `BuilderRoleVariant` both implement `markNeedsReload()` which sets
> `BuilderInfo.State.NEEDS_RELOAD`. No server restart needed.
>
> This means the editor CAN offer a live edit-save-observe workflow. Save the JSON → engine
> hot-reloads → modder sees updated NPC behavior in-game immediately. This is a significant
> UX advantage over hand-editing (where the modder may not know hot-reload exists).

**5.4** Does the engine enforce state machine topology constraints (connected graph,
reachable from start, no deadlock states)?

> **ANSWERED:** Partial enforcement. `StateMappingHelper.validate()` checks:
> - Every state that has a setter (transition TO it) must have a sensor (handler FOR it),
>   and vice versa — orphan states are flagged as errors.
> - States required by parameters must exist as sensor states.
> - State evaluators must be defined if required by the template.
> - `StartState` must be non-empty (defaults to `"start"`).
> - State strings must follow `MainState.SubState` naming convention.
>
> The engine does **NOT** check for reachability (all states reachable from StartState) or
> deadlock (states with no exit transitions). These are **optional warnings** the editor
> could provide as value-add static analysis beyond what the engine enforces.

**5.5** What is the engine's behavior for invalid `{Compute: "..."}` expressions?

> Load-time failure? Runtime error? Silent default? This determines whether the editor
> needs a Compute expression validator.

> **ANSWERED:** The engine has a **full expression compiler** (`Token.java`, `ValueType.java`)
> with a well-defined operator set and type system:
>
> | Category | Operators |
> |---|---|
> | Arithmetic | `+`, `-`, `*`, `/`, `%`, `**` (exponentiation) |
> | Comparison | `>`, `>=`, `<`, `<=`, `==`, `!=` |
> | Logical | `&&`, `||`, `!` |
> | Bitwise | `&`, `|`, `^`, `~` |
> | Grouping | `(`, `)`, `[`, `]` (tuples/arrays), `,` |
> | Literals | Numbers, quoted strings, identifiers (parameter names) |
> | Other | Function calls |
>
> Value types: `VOID`, `NUMBER`, `STRING`, `BOOLEAN`, `EMPTY_ARRAY`, `NUMBER_ARRAY`,
> `STRING_ARRAY`, `BOOLEAN_ARRAY`.
>
> Expressions are **compiled and type-checked at load time** via `BuilderParameters.compile()`.
> Invalid expressions throw `RuntimeException`, preventing the NPC from loading. The `Eval`
> sensor (marked Experimental) uses the same engine but exposes runtime NPC properties
> (`health`, `blocked`) in scope and requires a `BOOLEAN` return type.
>
> The editor needs a Compute expression widget with: identifier autocomplete (from
> Parameters scope), operator awareness, and ideally type-checking feedback before save.

---

## Tier 2 — Answer Before Implementation

These questions shape the design and architecture. They depend on Tier 1 answers.

---

### 6. Visual Paradigm

**6.1** Is the correct visual paradigm a finite automaton diagram (state boxes + labeled
transition arrows), a behavior-tree-style node graph (input/output ports on every node),
or a hybrid (nodes + inspector panels)?

> Behavior tree editor conventions (port colors for success/failure, hierarchical flow,
> composite/leaf/decorator taxonomy) are specific to the behavior tree execution model and
> **do not transfer** to a flat state machine. The relevant prior art lineage is Unity
> Animator (Mecanim), not Behavior Designer or NodeCanvas. This paradigm choice must match
> the verified data model, not aesthetic preference.

> **CORRECTED:** The framing of this question assumed a flat FSM, making Unity Animator the
> primary reference. The verified data model invalidates that assumption. The actual paradigm
> must represent:
>
> 1. A **priority-ordered list** of Instructions (evaluated top-to-bottom; first match wins).
> 2. Each Instruction as a compound object with Sensor, Actions, BodyMotion, HeadMotion, and
>    child Instructions (nested recursively).
> 3. Optional **behavior tree mode** (`TreeMode: true`) on any Instructions block.
> 4. States as implicit groupings (Sensor `{Type: "State", State: "..."}`) rather than
>    explicit nodes.
>
> The closest prior art is a hybrid of Behavior Designer (for the tree structure) and a
> structured list editor (for priority ordering). A flat state diagram (Unity Animator
> style) would misrepresent the data. The visual paradigm must be revisited from scratch
> with this model in mind. Key open question: should the editor surface Instructions as a
> tree, as a sequential list with expand/collapse, or as a zoomable node graph?

**6.2** Should states be visually distinct from transitions, or are both represented as
nodes?

> Unity Animator uses heterogeneous canvas (state nodes + transition arrows). Behavior
> Designer represents everything as nodes. For a state machine, the heterogeneous approach
> (states are boxes, transitions are arrows with conditions) is more natural — but may not
> support complex compound conditions elegantly.

**6.3** Is there a concept of "start state," "any state," or "exit state" that requires
special visual treatment?

> Unity Animator has Entry, Any State, and Exit pseudo-nodes. If Hytale has analogous
> concepts, they need dedicated visual representations that cannot be deleted or duplicated.

**6.4** How are transition conditions authored — as data on the edge, or as separate nodes?

> Edge-label conditions (displayed on the arrow) are simpler to read but harder to compose.
> Condition nodes connected to the transition are more composable but produce denser graphs.
> The actual Hytale Condition schema (string? typed object? composable?) determines this.

**6.5** Do nodes have typed ports, or is the connection model untyped?

> If the state machine has no port-level type system (connections are simply "state A →
> state B"), importing typed port conventions from shader graphs would add complexity
> without value.

**6.6** Is a floating pan/zoom canvas the right paradigm, or would a simpler layout serve
most modders?

> For NPCs with 5-10 states, a fixed-layout view (decision table, structured list with
> inline transitions) might be more readable and less intimidating than a floating canvas.
> The pan/zoom convention became universal after Blueprints, not because it's optimal for
> all cases.

---

### 7. Architecture and Platform Integration

**7.1** Should the node editor use `TextEditorWithPreview` (text + node graph split) or be a
standalone `FileEditorProvider`?

> The existing split creates a **split-undo hazard**: IntelliJ's UndoManager routes Ctrl+Z
> to the focused component. If the user clicks in the text panel after graph mutations,
> Ctrl+Z goes to the text editor's stack, not the graph's. This must be empirically tested
> with the existing UI editor before committing the pattern.

**7.2** Should the node editor follow the `.hyve-meta` sidecar precedent from hyve-prefab
for layout metadata?

> hyve-prefab already uses `.prefab.json.hyve-meta` sidecar files to persist editor state
> (selection, scroll, filter, collapse state). This is established precedent in the
> codebase. The NPC editor could use `.json.hyve-meta` files for node positions, zoom level,
> groupings. Trade-offs: sidecar files can be lost if not committed to version control; but
> they cleanly separate editor state from engine data.

**7.3** Should the node editor register mutations via `undoableActionPerformed()` (current
HyveUndoBridge pattern) or via `WriteCommandAction` / `CommandProcessor.executeCommand()`?

> The current pattern bypasses CommandProcessor, meaning undo entries are unnamed ("Undo"
> rather than "Undo: Connect Wire") and command grouping is handled entirely by the custom
> UndoManager's `canMergeWith`. For compound graph operations, explicit CommandProcessor
> grouping may be needed.

**7.4** What is the unit of undo for node graph operations?

> Should "connect port A to port B" and the resulting auto-layout be one undo entry or two?
> Should "move 5 nodes simultaneously" be one entry or five? This determines command
> granularity and whether `CompositeCommand` is needed.

**7.5** When a referenced file is modified externally (VCS checkout, another editor), how
does the node editor detect and respond?

> The existing `DocumentListener` pattern watches a single file. A multi-file editor needs
> listeners on every referenced file. Reactive (re-validate on every change) vs lazy
> (re-validate on focus) has performance and correctness trade-offs.

**7.6** What is the `isModified()` contract when changes span multiple VirtualFiles?

> If the editor mutates three files in one operation, which editor's dirty indicator
> activates? IntelliJ's tab "*" decoration is per-FileEditor. If another editor has the
> Drops file open, it may show stale state with no dirty indicator.

**7.7** Should NPC JSON files get a custom PSI implementation for structural IntelliJ
features (Find Usages, Go to Declaration, Rename Refactoring)?

> PSI integration enables project-wide "find all NPCs that reference this drop table" and
> "rename this NPC and update all references." Without PSI, these are manual tasks. With
> PSI, they leverage IntelliJ's refactoring infrastructure. Alternative: use the knowledge
> graph for these queries (via `GraphTraversal`), but this creates a hard dependency on
> hyve-knowledge.

**7.8** How does the save strategy avoid a feedback loop when the editor watches files it also writes?

> Every VFS write triggers a document change event, which loops back to the editor if it's
> watching those files. The save strategy must be chosen with this loop in mind.

---

### 8. Rendering and Canvas Architecture

**8.1** Should nodes be rendered as Compose `Layout` composables (one composable per node) or
as `DrawScope` draw calls inside a single Canvas composable?

> DrawScope: consistent with hyve-ui pattern, no automatic hit-testing, full control,
> no per-node recomposition overhead. Per-node composables: automatic keyboard focus,
> standard accessibility, hit-testing via Modifier.pointerInput, but all nodes participate
> in layout even when off-screen. The existing CanvasView uses DrawScope — this is proven
> for the UI editor.

**8.2** Which components of hyve-ui's canvas stack (`CanvasState`, `CanvasPainter`,
`LayoutEngine`) are reusable?

> Reusable: zoom/pan state management, `treeVersion` recomposition trigger, cursor
> throttling (16ms), `drawWithCache` optimization. Must be replaced: `LayoutEngine`
> (tree layout, not graph), `CanvasPainter` (renders UIElement trees, not nodes/wires),
> `ElementBounds` (needs port positions for nodes).

**8.3** How should wire connections be rendered and routed?

> Options: straight lines (trivial, ugly), orthogonal/Manhattan (clean for DAGs, complex
> algorithm), cubic Bezier (aesthetic, standard for node editors, but proximity hit-testing
> requires parametric sampling). The choice has cascade effects on hit-testing, rendering
> performance, and visual clarity.

**8.4** How should node/port/wire hit-testing work?

> `findElementAt()` is O(n) — fine for 50 nodes. Wire hit-testing (detecting cursor
> proximity to a Bezier curve) is not solvable with bounding box containment — requires
> geometric sampling or a spatial index. The decision to introduce a quadtree/R-tree is
> driven by wire hit-testing requirements, not node count.

**8.5** Should graph layout be manual (user positions nodes), automatic (algorithm places
nodes), or hybrid?

> Manual requires positions stored somewhere (see 7.2). Automatic requires choosing an
> algorithm (force-directed: natural but non-deterministic; hierarchical Sugiyama: clean for
> DAGs, breaks on cycles; tree: only for strict trees). Hybrid is most user-friendly but
> most implementation-intensive.

**8.6** How should recomposition be scoped to avoid 60fps full-canvas recompositions during
wire drag?

> The existing CanvasView reads six state values at the composable level — any change
> triggers full recomposition. During wire drag (60fps pointer events), this means 60
> outer recompositions/second. Mitigation: `derivedStateOf`, `snapshotFlow`, or `@Stable`
> annotation on graph state to narrow recomposition scope.

**8.7** How should port connection drag interact with canvas pan/zoom gestures?

> Port connection drag (press on port, drag to target) must not conflict with canvas pan
> (space+drag). What happens if the user presses space mid-drag? What if they drag a
> connection to the viewport edge (auto-scroll)?

---

### 9. Modder UX and Workflow

**9.1** What does a modder's NPC creation session actually look like, start to finish?

> Do they plan the full behavior graph on paper first, then encode it? Or write one state,
> test in-game, iterate? Authoring pattern determines whether the editor should be
> canvas-first or list-first.

**9.2** What is the most common NPC task — creating new NPCs, modifying existing ones, or
creating variants of base-game NPCs?

> If 80% of time is spent creating variants (Reference + Modify), the primary UI should be
> a "diff view" showing what changed from the base, not a full graph.

**9.3** What are the specific moments where modders get stuck, lose time, or make errors
when hand-editing NPC JSON?

> The node editor should eliminate real pain, not hypothetical pain. If modders most often
> break cross-file references, the reference browser is the MVP. If they can't understand
> state flow, the graph view is the MVP.

**9.4** How does the modder discover what node types (Actions, Sensors, BodyMotion, etc.)
are available?

> The palette IS the documentation. Organization: by category? alphabetical? most-recently-
> used? Search must be available. Each type needs a description. Without discoverability,
> modders face the same "what options exist?" problem they have now.

> **ANSWERED:** All 183 builder types are fully documented in `npc-doc.mdx` with attributes,
> types, constraints, and stability status. Per 14.1, this is a **closed set** — no plugin
> extensibility. The palette is a **static list** that can be generated directly from
> `npc-doc.mdx` and categorized by builder class: Sensor, Action, BodyMotion, HeadMotion,
> MotionController, IEntityFilter, Instruction variants. Each type already has descriptions.
> The remaining open question is only the palette's **interaction design** (search, category
> grouping, MRU, drag-to-canvas) — a UX decision, not a data question.

**9.5** Should palette contents be hardcoded or generated from the Knowledge index?

> **ANSWERED:** The palette should be **generated from `npc-doc.mdx`** (or its parsed
> equivalent in the Knowledge index), not hardcoded. Since builder types are a closed set
> (see 14.1), the palette doesn't need runtime discovery. However, generating from the
> Knowledge index means it automatically updates when Hyve re-indexes a new Hytale version's
> `npc-doc.mdx`. Hardcoding would require a Hyve code change on every Hytale update.
> The Knowledge index approach is the right trade-off: static enough to be reliable,
> dynamic enough to track Hytale updates without code changes.

**9.6** What is the first-open experience — what does a modder see when they open an NPC file
in the node editor for the first time?

> An empty canvas with no guidance will cause immediate abandonment. The editor should
> parse existing NPC JSON and render the current state visually. For new files, starter
> templates or the Knowledge panel's indexed NPCs can provide starting points.

**9.7** How does the editor represent the Reference + Modify inheritance pattern visually?

> Variant NPCs override a base NPC's fields. The visual metaphor must accurately reflect
> the data model without implying a class hierarchy that doesn't exist in the engine.

**9.8** What is the keyboard-driven workflow for power users?

> A mouse-only editor will be rejected by experienced modders. Tab to cycle nodes, Enter
> to connect, Ctrl+Z for undo, Ctrl+D to duplicate — IntelliJ's shortcut system must be
> respected.

**9.9** When the graph becomes large (50+ states for a complex boss), how does the editor
prevent illegibility?

> Without grouping/collapsing, minimap, search-to-highlight, and zoom-to-fit, the node
> editor makes large NPCs WORSE than JSON (which at least supports Ctrl+F).

---

### 10. Error Prevention and Validation

**10.1** What validation runs in real-time vs on-save vs on-export?

> Real-time validation provides the tightest feedback but can distract. On-save is
> conventional. On-export is a safety net. The default for each layer must be decided.
> IntelliJ's inspection system provides a pattern to follow.

**10.2** What constitutes a "valid" NPC — the editor's schema, or what the engine accepts?

> These may diverge. The editor's schema is reverse-engineered. Validation errors should be
> presented as "warnings" (possible issue) rather than "errors" (definitely wrong) unless
> confidence is high.

**10.3** What static topology checks can the editor perform?

> Detectable without running the game: unreachable states (no path from start), sink states
> (no outgoing transitions), empty state machines. These catch real bugs that currently
> require in-game testing.

**10.4** How does the editor handle fields it doesn't know about?

> Unknown fields must be preserved verbatim and shown in a "raw properties" section.
> Dropping unknown fields on save is a **fatal trust violation** that will cause modders
> to abandon the editor.

**10.5** How does the editor surface broken cross-file references?

> A red connection line? Error panel? Inline badge? The modder must be able to fix them —
> via typing a corrected filename, or using a picker showing valid file names.

---

### 11. Accessibility

**11.1** Is accessibility (keyboard navigation, screen reader support, color-blind-safe port
colors) a day-one requirement or accepted technical debt?

> Every major shipped node editor has poor accessibility and none successfully retrofitted
> it. Accessibility must be designed into the interaction model — it cannot be added
> afterward. This is an implicit decision that teams make silently; it should be made
> explicitly.

---

## Tier 3 — Answer Before Shipping

These questions determine the product's viability and long-term success.

---

### 12. Round-Trip Fidelity

**12.1** What is the exact guarantee the editor makes about preserving hand-written JSON?

> Field order? Formatting? Comments? Unknown fields? This guarantee must be explicit and
> written down **before** the parser is built. Reference: hyve-prefab preserves rawJson
> and byte-splices only touched entities — a similar strategy should be designed for NPC files.

**12.2** If a modder has edited an NPC JSON file by hand, what happens when they open it in
the node editor?

> The editor must re-parse on every open and render from current JSON state, not cached
> state. Unknown content must appear in a "raw" section, never silently dropped.

**12.3** Can a modder use the node editor for some files and hand-edit others without
consequences?

> Yes — this must be guaranteed. The node editor is opt-in per file. There must be no
> persistent state about a file beyond what the file itself contains (layout metadata
> in sidecar files is acceptable; required metadata in the game file is not).

**12.4** Does the editor produce deterministic, minimal JSON output that generates clean
git diffs?

> Non-deterministic output (random field ordering, timestamp metadata) makes git diffs
> unreadable and code review impossible. This is a non-negotiable requirement for any
> mod team using version control.

---

### 13. Debugging and Testing

**13.1** Can the editor show which state an NPC is currently in during a live server session?

> **PARTIALLY ANSWERED:** The engine has a comprehensive **34-flag debug system**
> (`RoleDebugFlags.java`), including:
>
> - **Display flags** (shown on NPC nameplate): `DisplayState`, `DisplayTarget`,
>   `DisplayAnim`, `DisplayHP`, `DisplayStamina`, `DisplaySpeed`, `DisplayFlock`,
>   `DisplayCustom`, `DisplayFreeSlots`, `DisplayInternalId`, `DisplayName`, `Pathfinder`
> - **Trace flags** (console logging): `TraceFail`, `TraceSuccess`, `TraceSensorFailures`,
>   `Flock`, `MotionControllerSteer`, `Collisions`, `BeaconMessages`
> - **Visualization flags** (in-world overlays): `VisAvoidance`, `VisSeparation`,
>   `VisAiming`, `VisMarkedTargets`, `VisSensorRanges`, `VisLeashPosition`, `VisFlock`
> - **Presets**: `"display"` (all Display flags), `"all"`, `"move"`, `"steer"`, `"default"`
>
> Flags are set via the `"Debug"` field in JSON or at runtime via `NPCCommand`. The
> `TraceSuccess`/`TraceFail` flags log which instructions match/fail — this IS parseable
> state execution data. Whether the editor can connect to a running server to read these
> logs in real-time is an integration question, but the engine exposes the data. The
> architecture should plan for this — it's the biggest differentiator.

**13.2** What static analysis can catch bugs before in-game testing?

> Even without runtime integration: unreachable states, deadlock states, transitions with
> impossible conditions (if condition types support analysis), drop tables with zero items,
> circular group references. Each catches a class of real bugs.

**13.3** How does a modder verify that their intended behavior ("patrol when idle, chase on
sight, return after 5 seconds") matches what they built?

> Could the editor support stepping through states with hypothetical conditions? At minimum,
> it should make reading the graph in natural language easy — not just as data.

---

### 14. Extensibility

**14.1** Can modders define custom Action types, Condition types, and Sensor types?

> If `Actions[].Type` and `Sensor.Type` are open enums (any string), modders can create
> custom behaviors via plugins. If closed (engine-defined only), the editor can provide a
> complete dropdown. This determines whether the palette is a fixed list or a plugin registry.

> **ANSWERED:** The 183 builder types are a **closed set** — each maps to a hardcoded Java
> class in `com.hypixel.hytale.server.npc.corecomponents`. There is **no documented API**
> for modders to register custom Action/Sensor/BodyMotion/HeadMotion types via JSON or via
> Java plugins. The `"Type"` field in JSON maps directly to a specific builder class.
>
> Modders extend NPC behavior through **composition** within the existing type set:
> Components (reusable instruction fragments), Variants (template inheritance + overrides),
> Parameters (computed expressions), and the full Instruction nesting system. For behavior
> beyond what the 183 builders support, Java plugins interact at the **ECS level**
> (custom systems, components, event handlers via `NPCPlugin.get()`) rather than extending
> the NPC builder registry.
>
> The palette CAN be a **static list** derived from `npc-doc.mdx`. Plugin-defined builder
> types are not a supported pattern.

**14.2** How does the editor represent custom mod-defined node types?

> **ANSWERED:** This question is moot given 14.1. Modders cannot define custom builder types.
> The editor's type palette is the complete 183-type registry from `npc-doc.mdx`. Advanced
> modders who need behavior beyond the built-in types work at the ECS/plugin level, which
> is outside the scope of the NPC JSON editor entirely. The editor does not need a plugin
> discovery mechanism for custom node types.

**14.3** What happens to the node editor when a Hytale update changes the NPC data format?

> Recovery strategy: version-tagged schemas, migration that attempts to update each node,
> flags unmigrateable nodes, shows what changed. Migration failure must never result in
> data loss — must fall back to showing pre-migration JSON.

**14.4** What is the editor's policy for files created with an older version of the editor?

> Older files must open without error in newer versions. Deprecated nodes shown as "legacy"
> rather than deleted. The editor must never refuse to open a file it created.

**14.5** How does the editor communicate when its built-in schema is out of date?

> "Your Knowledge index is from Hytale version X. You are running Y. Consider re-indexing."

---

### 15. Shipping Strategy

**15.1** What is the smallest version that delivers more value than the status quo for at
least one common task?

> This is the MVP definition. It must name a specific task and a specific claim:
> "A modder creating a new NPC variant can do so in half the time because cross-file
> reference linking prevents broken references." The MVP scope flows from this claim.

**15.2** What ships first — the instruction tree view, the cross-file reference browser,
the palette, or the NPC property form?

> Each can ship independently. The instruction tree view is highest-risk (requires
> understanding the full nested Instructions model). The cross-file reference browser
> addresses concrete, well-understood pain (broken links). The property form (like the
> prefab editor) is lowest risk. Ship the lowest-risk highest-value piece first to get
> user feedback earliest.

**15.3** What are the V1 success metrics?

> Possible: modders report fewer broken-reference bugs, time-to-first-working-NPC decreases,
> the editor is used for X% of NPC edits. Without metrics, "was this worth it?" cannot be
> answered.

**15.4** What is the team's capacity for ongoing maintenance as Hytale updates?

> Every feature is a future maintenance liability. When Hytale adds a field, someone must
> update the schema model. Maintenance capacity directly constrains V1 scope.

---

### 16. Community and Collaboration

**16.1** Can modders share reusable sub-graphs (common behavior patterns) through the
community?

> Patrol-and-return, flee-when-low-health, respawn-timer — these are common patterns many
> NPCs share. The Component system (`{Reference: "ComponentName"}`) in DecisionMaking/ is
> the engine's native mechanism for this. Exportable/importable Component files enable a
> community library using the engine's own reuse mechanism.

**16.2** What happens to a mod project when shared with someone who doesn't have Hyve?

> The node editor's output is JSON files — plain text that works without Hyve. This must be
> verified: the editor is a CREATION tool, not a RUNTIME dependency. No Hyve-specific
> metadata may be embedded in engine-consumable files.

**16.3** When a modder renames a file referenced by other NPC files, does the editor offer
to update all references?

> This is the "rename refactoring" question. If supported, it needs the full file graph.
> If not, renamed files silently break all referencing NPCs.

---

## Dependency Graph

```
Tier 1 (gate everything):
  1. Paradigm Justification ──→ gates: all of Tier 2
  2. Scope & Boundaries ──────→ gates: 7 (Architecture), 9 (UX), 15 (Shipping)
  3. Data Model Verification ─→ gates: 6 (Visual Paradigm), 8 (Rendering), 10 (Validation)
  4. Cross-File Topology ─────→ gates: 7 (Architecture), 9.4-9.7 (UX), 10.5 (Validation)
  5. Engine Constraints ──────→ gates: 7.2 (Sidecar vs embedded), 10 (Validation), 12 (Round-Trip)

Tier 2 (shape design):
  6. Visual Paradigm ─────────→ gates: 8 (Rendering), 9.4-9.9 (UX)
  7. Architecture ────────────→ gates: 8 (Rendering), 12 (Round-Trip)
  8. Rendering ───────────────→ gates: 11 (Accessibility)
  9. UX & Workflow ───────────→ gates: 15 (Shipping Strategy)

Tier 3 (ship it):
  12. Round-Trip Fidelity
  13. Debugging
  14. Extensibility
  15. Shipping Strategy
  16. Community
```

---

## Critical Path: Remaining Design Decisions

All factual/research questions from Tier 1 are now answered. The remaining critical path
consists of **design decisions** that require human judgment, not further research:

1. **6.1** — Choose the visual paradigm for a nested priority-list / BT hybrid. This is the
   most consequential unresolved design question. The data model is fully understood; the
   question is how to represent it visually (tree view, node graph, structured list, or hybrid).
2. **2.2** — Decide single-file (Role only) vs multi-file scope (Role + Components in unified view).
   The cross-file reference graph is now fully mapped (4.2), so this is a scoping decision.
3. **1.1** — Articulate specifically why a node editor beats improved text tooling for the
   nested Instructions model. The case is strong (900-line templates with 3-4 nesting levels,
   hot-reload support for live feedback, 34-flag debug system), but must be stated explicitly.
4. **15.1** — Define the MVP: what ships first, and what specific claim does it make?
5. **7.1** — Choose `TextEditorWithPreview` (split view) vs standalone `FileEditorProvider`,
   informed by the split-undo hazard and the fact that hot-reload exists.

Previously on critical path, now resolved:
- **3.2** — CORRECTED: no monolithic DecisionMaking schema
- **3.4** — ANSWERED: hierarchical (nested Instructions + substates + TreeMode)
- **3.5** — ANSWERED: Variant→Template→Component layering, templates are 200-900+ lines
- **4.1** — CORRECTED: Role files contain Instructions directly
- **4.2** — ANSWERED: complete cross-file reference graph mapped
- **4.3** — ANSWERED: always bare filename stems
- **4.4** — ANSWERED: cycles impossible, engine validates DAG
- **5.1-5.5** — ANSWERED: strict validation, multi-level error handling, hot-reload, full
  expression compiler

---

## Sources

This question set was synthesized from four analytical perspectives:

- **Domain Architecture**: Hytale data model, schema precision, cross-file reference topology
- **Platform Engineering**: IntelliJ/Compose Desktop constraints, undo/redo, rendering, VFS
- **UX Design**: Modder workflow, paradigm justification, shipping strategy, hidden assumptions
- **Prior Art Analysis**: Lessons from Unreal Blueprints, Unity Animator, Godot VisualScript (deprecated),
  Behavior Designer, NodeCanvas, FlowCanvas, Node-RED, Houdini, xNode, Shader Graph

All questions are grounded in the actual Hyve codebase (`hyve-ui`, `hyve-prefab`, `hyve-knowledge`,
`hyve-common`) and in the Hytale game data formats as discovered through `GameDataParser.kt`,
`GameDataTextBuilder.kt`, `GraphTraversal.kt`, and `knowledge-graph-schema.md`.

Corrections and answers added 2026-02-22 based on official Hytale documentation (`npc-doc.mdx`)
covering the complete 183-type builder registry, Role file schema, Component system, and
StateTransition architecture.

Second pass answers added 2026-02-22 from decompiled engine code analysis:
- `RawJsonReader.java` — strict JSON parsing, no JSONC
- `InternalReferenceResolver.java` — DAG validation, cycle detection
- `StateMappingHelper.java` — state setter/sensor consistency validation
- `NPCLoadTimeValidationHelper.java` — load-time schema validation
- `Token.java` / `ValueType.java` — full expression compiler operator set
- `FlockAsset.java` / `AttitudeGroup.java` / `NPCGroup.java` — file schemas
- `AssetMonitor` / `PathWatcherThread` — hot-reload system
- `RoleDebugFlags.java` — 34-flag debug system
- `BuilderRole.java` / `BuilderRoleVariant.java` — complete Role field inventory
- `BuilderCombatConfig.java` — reference validation patterns
- Tutorial appendix (`12-appendix.mdx`) — complete ogre template JSON for casing analysis
