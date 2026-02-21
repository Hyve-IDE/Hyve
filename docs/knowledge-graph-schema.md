# Knowledge Graph Schema v3

> Comprehensive schema for the Hyve Knowledge plugin's graph database.
> Defines all node types, edge types, extraction rules, embedding text templates,
> and metadata fields across all 4 corpora.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Node Types & Granularity](#2-node-types--granularity)
3. [Edge Taxonomy](#3-edge-taxonomy)
4. [Embedding Text Templates](#4-embedding-text-templates)
5. [Metadata Fields](#5-metadata-fields)
6. [ID Resolution Strategy](#6-id-resolution-strategy)
7. [Search Integration](#7-search-integration)
8. [Migration Plan](#8-migration-plan)
9. [Implementation Priorities](#9-implementation-priorities)

---

## 1. Architecture Overview

The knowledge graph uses a dual-index architecture:
- **SQLite** — Graph structure (nodes + typed edges) and metadata
- **JVector HNSW** — Per-corpus vector indexes for semantic search

Four corpora feed the graph:

| Corpus | Source | Node Count | Granularity |
|--------|--------|-----------|-------------|
| CODE | Decompiled Hytale JAR | ~15K methods + classes | Method-level |
| GAMEDATA | Assets.zip (24 data types) | ~28K files | Whole-file |
| CLIENT | Client UI files (.xaml, .ui, .json) | ~250 files | Whole-file |
| DOCS | HytaleModding.dev | ~100 pages | Whole-document |

### Design Principles

1. **Every edge must enable a search scenario that currently fails.** No edge exists without a concrete query it answers.
2. **Typed, directed edges replace generic RELATES_TO.** "What ingredients does X need?" requires directionality — generic "related" edges can't answer it.
3. **Embedding text must match query vocabulary.** If "crafting recipe" never appears in an item's text, vector search will never find it for recipe queries.
4. **The graph compensates for vector limitations.** Structural queries ("what drops from goblins?") are graph queries, not vector queries.
5. **Whole-file granularity for gamedata is retained.** Typed edges + enriched embedding text solve search failures without the complexity of sub-node splitting.

### Why Not Sub-Nodes?

The HNSW index uses `chunk_index` (insertion-order integer) as the vector ordinal, mapped back to node IDs via SQL. Adding sub-nodes increases total embeddings significantly (~28K → ~80K+), proportionally increasing embedding cost and HNSW build time. The search failures that motivated this redesign (torch not found for "how to craft torches") are fully solved by:
- Adding inline Recipe data to ITEM embedding text (vector search fix)
- Adding REQUIRES_ITEM edges from items to their ingredients (graph search fix)

Both work at whole-file granularity. Sub-nodes become justified only if precision@5 for ingredient-lookup queries drops below 0.4 after implementing the above.

---

## 2. Node Types & Granularity

### 2.1 CODE Corpus (unchanged)

| Node Type | ID Format | Embedded? | Purpose |
|-----------|-----------|-----------|---------|
| JavaMethod | `method:com.hypixel.hytale.foo.Bar#baz` | Yes | Searchable code units |
| JavaClass | `class:com.hypixel.hytale.foo.Bar` | No (graph-only) | Graph anchors for edges |
| Package | `pkg:com.hypixel.hytale.foo` | No (graph-only) | Hierarchical grouping |

### 2.2 GAMEDATA Corpus (unchanged granularity, enriched content)

| Node Type | ID Format | Embedded? | Purpose |
|-----------|-----------|-----------|---------|
| GameData | `gamedata:Server:Item:Items:Torch.json` | Yes | All 24 data types |

The 24 `data_type` values: item, recipe, block, interaction, drop, npc, npc_group, npc_ai, entity, projectile, farming, shop, environment, weather, biome, localization, zone, terrain_layer, cave, prefab, worldgen, camera, objective, gameplay.

### 2.3 CLIENT Corpus (unchanged)

| Node Type | ID Format | Embedded? | Purpose |
|-----------|-----------|-----------|---------|
| ClientUI | `xaml:DesignSystem/MainMenu.xaml` | Yes | UI templates and components |
| ClientUI | `ui:InGame/Hotbar.ui` | Yes | Hytale custom UI files |

### 2.4 DOCS Corpus (unchanged)

| Node Type | ID Format | Embedded? | Purpose |
|-----------|-----------|-----------|---------|
| Doc | `docs:guides/block-creation.md` | Yes | Modding documentation |

### 2.5 Virtual References (NEW)

Virtual references are synthetic target IDs for entities that have no corresponding node type. They use `target_resolved=0` in the edges table and enable reverse queries without polluting the nodes table.

| Virtual Type | ID Format | Example | Purpose |
|-------------|-----------|---------|---------|
| Particle System | `virtual:particle:{systemId}` | `virtual:particle:Torch_Fire` | 5+ source types reference particles |
| Crafting Bench | `virtual:bench:{benchId}` | `virtual:bench:Workbench` | Recipes reference crafting stations |
| Status Effect | `virtual:effect:{effectId}` | `virtual:effect:Damage_High` | Interactions apply effects |
| Resource Type | `virtual:resource:{typeId}` | `virtual:resource:Wood_Trunk` | Recipe inputs reference abstract resources |

Virtual references are created as edge targets only. They have no node row, no embedding, and no content. They enable queries like "what entities use the Torch_Fire particle?" via:
```sql
SELECT source_id FROM edges WHERE edge_type = 'SPAWNS_PARTICLE' AND target_id = 'virtual:particle:Torch_Fire'
```

---

## 3. Edge Taxonomy

### 3.0 Existing Code Corpus Edges (unchanged)

| Edge Type | Direction | Semantics |
|-----------|-----------|-----------|
| EXTENDS | JavaClass → JavaClass | Inheritance |
| IMPLEMENTS | JavaClass → JavaClass | Interface implementation |
| CONTAINS | Package → JavaClass | Package hierarchy |

### 3.1 Priority 1 — Core Gamedata Edges

These 6 edge types replace the generic RELATES_TO and directly fix the most common search failures.

---

#### REQUIRES_ITEM

**Direction:** RECIPE → ITEM (standalone) or ITEM → ITEM (inline recipe)

**Semantics:** This item/resource is a required ingredient.

**Search scenario:** "What do I need to craft a torch?" → find Torch node → follow REQUIRES_ITEM → ingredient items.

**Extraction rules:**

| Source Type | Field Path | Target Resolution |
|-------------|-----------|-------------------|
| RECIPE | `Input[i].ItemId` | Stem match → ITEM node |
| RECIPE | `inputs[i].item` | Stem match → ITEM node |
| RECIPE | `ingredients[i].id` | Stem match → ITEM node |
| ITEM (inline) | `Recipe.Input[i].ItemId` | Stem match → ITEM node |
| RECIPE / ITEM | `Input[i].ResourceTypeId` | `virtual:resource:{id}` (target_resolved=0) |

**Note on inline recipes:** Items with a `Recipe` field (e.g., Torch, Cooking Bench) create REQUIRES_ITEM edges where the source is the item node itself. The item is implicitly both the recipe and its output — no PRODUCES_ITEM edge is needed for inline recipes.

---

#### PRODUCES_ITEM

**Direction:** RECIPE → ITEM

**Semantics:** This recipe produces this item as output.

**Search scenario:** "What recipes make iron ingots?" → find ITEM node → reverse-traverse PRODUCES_ITEM → RECIPE nodes.

**Extraction rules:**

| Source Type | Field Path | Notes |
|-------------|-----------|-------|
| RECIPE | `PrimaryOutput.ItemId` | Primary output |
| RECIPE | `Output[i].ItemId` | Secondary outputs |
| RECIPE | `result.ItemId` | Legacy format |
| RECIPE | `outputs[i].item` | Legacy camelCase |

**Edge metadata:** `{"role": "primary"}` for PrimaryOutput, `{"role": "secondary"}` for Output[].

**Not applicable for inline recipes** — the item IS the output. Only standalone recipe files produce PRODUCES_ITEM edges.

---

#### DROPS_ITEM

**Direction:** DROP → ITEM

**Semantics:** This item can appear as loot from this drop table.

**Search scenario:** "What items are in the goblin loot table?" → find DROP node → follow DROPS_ITEM → ITEM nodes.

**Extraction rules:**

Built during DROP node indexing by traversing the Container hierarchy:
- `Container.Item.ItemId`
- `Container.Containers[i].Item.ItemId`  (recursive)
- `Drops[i].ItemId` / `drops[i].item` / `entries[i].id` (flat fallback)

**Edge metadata:** `{"chance": 0.15, "qty_min": 1, "qty_max": 3}` when available.

---

#### DROPS_ON_DEATH

**Direction:** NPC | ENTITY | BLOCK | FARMING → DROP

**Semantics:** This entity produces this loot table when killed, mined, or harvested.

**Search scenario:** "What drops from goblins?" → find NPC node → follow DROPS_ON_DEATH → DROP node → follow DROPS_ITEM → ITEM nodes.

**Extraction rules:**

| Source Type | Field Path | Notes |
|-------------|-----------|-------|
| NPC | `DropList` (root) | Primary form |
| NPC | `Modify.DropList` | Variant NPC form |
| ENTITY | `Drops` | String or object with id field |
| BLOCK | `Drops` / `lootTable` | Block mining drops |
| FARMING | `ProduceDrops` values | Coop animal produce |

**ID Resolution:** Value is a filename stem (e.g., `"Drop_Goblin_Warrior"`) → match against DROP nodes by lowercase filename stem.

---

#### OFFERED_IN_SHOP

**Direction:** SHOP → ITEM

**Semantics:** This item can be purchased at this shop.

**Search scenario:** "Where can I buy leather armor?" → find ITEM node → reverse-traverse OFFERED_IN_SHOP → SHOP nodes.

**Extraction rules:**

| Source Type | Field Path | Notes |
|-------------|-----------|-------|
| SHOP | `TradeSlots[i].Trade.Output.ItemId` | Fixed trade output |
| SHOP | `TradeSlots[i].Trades[i].Output.ItemId` | Pool trade output |
| SHOP | `Items[i].ItemId` | Legacy format |

Also extract cost items for metadata: `Trade.Input[i].ItemId` stored as edge metadata `{"cost_item": "Gold_Coin", "cost_qty": 5}`.

---

#### HAS_MEMBER

**Direction:** NPC_GROUP → NPC

**Semantics:** This NPC is a member of this group.

**Search scenario:** "What NPCs are in the Goblin Raiding Party?" → find NPC_GROUP → follow HAS_MEMBER → NPC nodes.

**Extraction rules:**

| Source Type | Field Path |
|-------------|-----------|
| NPC_GROUP | `Members[i].NPC` / `Members[i].npc` / `Members[i].id` |
| NPC_GROUP | `NPCs[i].Id` / `NPCs[i].id` |

**Bidirectionality:** Also insert reverse edge BELONGS_TO_GROUP (NPC → NPC_GROUP) in the same pass, synthesized from the same data.

---

### 3.2 Priority 2 — Supplementary Gamedata Edges

These enable important but less frequent search patterns.

---

#### TARGETS_GROUP

**Direction:** NPC | OBJECTIVE | FARMING → NPC_GROUP

**Semantics:** This entity targets, restricts behavior to, or filters by this NPC group.

**Search scenario:** "Which objectives require killing goblin groups?" → find NPC_GROUP → reverse-traverse TARGETS_GROUP → OBJECTIVE nodes.

**Extraction rules:**

| Source Type | Field Path |
|-------------|-----------|
| NPC | `TargetGroups[i]` / `Modify.TargetGroups[i]` |
| NPC | `AcceptedNpcGroups[i]` |
| OBJECTIVE | `TaskSets[i].Tasks[i].NPCGroupId` (for KillSpawnMarker tasks) |
| FARMING | `AcceptedNpcGroups[i]` (coop livestock) |

---

#### REQUIRES_BENCH

**Direction:** RECIPE | ITEM (inline recipe) → `virtual:bench:{benchId}`

**Semantics:** This recipe must be crafted at this bench/station.

**Search scenario:** "What can I craft at the forge?" → query `WHERE edge_type='REQUIRES_BENCH' AND target_id='virtual:bench:Forge'` → RECIPE/ITEM nodes → follow PRODUCES_ITEM for outputs.

**Extraction rules:**

| Source Type | Field Path |
|-------------|-----------|
| RECIPE | `BenchRequirement[i].Id` / `BenchRequirement` (string) / `station` |
| ITEM (inline) | `Recipe.BenchRequirement[i].Id` |

---

#### SPAWNS_PARTICLE

**Direction:** NPC | PROJECTILE | WEATHER | BLOCK | ENTITY → `virtual:particle:{systemId}`

**Semantics:** This entity displays this particle system.

**Search scenario:** "What uses the Torch_Fire particle effect?" → reverse query on SPAWNS_PARTICLE.

**Extraction rules:**

| Source Type | Field Path | Edge Metadata |
|-------------|-----------|---------------|
| PROJECTILE | `HitParticles.SystemId` | `{"trigger":"hit"}` |
| PROJECTILE | `DeathParticles.SystemId` | `{"trigger":"death"}` |
| WEATHER | `Particle.SystemId` | `{"trigger":"ambient"}` |
| BLOCK | `Particles.{event}` (each key) | `{"trigger":"{event}"}` |
| ENTITY | `ApplicationEffects.Particles[i]` | `{"trigger":"applied"}` |
| ITEM | `BlockType.Particles[i].SystemId` | `{"trigger":"block_state"}` |

---

#### APPLIES_EFFECT

**Direction:** INTERACTION → `virtual:effect:{effectId}`

**Semantics:** This interaction applies this status effect.

**Extraction rules:**

| Source Type | Field Path |
|-------------|-----------|
| INTERACTION | `Effects[i].EffectId` |
| INTERACTION | `Action` (when action type is ApplyEffect) |

---

#### REFERENCES_WORLDGEN

**Direction:** ZONE → WORLDGEN

**Semantics:** This zone uses this world generation noise configuration.

**Extraction rules:**

| Source Type | Field Path |
|-------------|-----------|
| ZONE | `NoiseMask.File` (references noise definition) |
| ZONE | Nested `Noise[i]` that reference external definitions |

---

### 3.3 Priority 3 — Cross-Corpus Edges

#### IMPLEMENTED_BY (existing, refined)

**Direction:** GameData → JavaClass

**Semantics:** This game data type is implemented by these Java classes.

**Current behavior (keep):** Uses `SystemClassMapping` to map data types to implementing classes. Every gamedata node of a given type links to the same set of classes for that type.

**Refinement needed:** Scope the edge deletion in `GameDataIndexerTask` to only delete gamedata-owned edge types:

```sql
-- BEFORE (BROKEN — destroys cross-corpus edges):
DELETE FROM edges WHERE source_id LIKE 'gamedata:%' OR target_id LIKE 'gamedata:%'

-- AFTER (FIXED — only deletes gamedata-owned edges):
DELETE FROM edges
WHERE (source_id LIKE 'gamedata:%' OR target_id LIKE 'gamedata:%')
  AND edge_type IN (
    'RELATES_TO', 'IMPLEMENTED_BY',
    'REQUIRES_ITEM', 'PRODUCES_ITEM', 'DROPS_ITEM', 'DROPS_ON_DEATH',
    'OFFERED_IN_SHOP', 'HAS_MEMBER', 'BELONGS_TO_GROUP',
    'TARGETS_GROUP', 'REQUIRES_BENCH', 'SPAWNS_PARTICLE',
    'APPLIES_EFFECT', 'REFERENCES_WORLDGEN'
  )
```

This protects future UI_BINDS_TO and DOCS_REFERENCES edges written by other indexers.

---

#### DOCS_REFERENCES (NEW)

**Direction:** Doc → JavaClass | GameData

**Semantics:** This documentation page references this code class or game data concept.

**Search scenario:** "What docs cover CraftingRecipe?" → find class node → reverse-traverse DOCS_REFERENCES → Doc nodes.

**Extraction rules:**
1. Scan markdown content for backtick spans (`` `ClassName` ``)
2. Scan for PascalCase words matching `\b[A-Z][a-z]+([A-Z][a-z]+)+\b`
3. Cross-reference against nodes table: only create edge when name exactly matches a `display_name` value

**Dependency:** Code and gamedata corpora must be indexed before docs edges are built.

---

#### UI_BINDS_TO (NEW, MEDIUM confidence)

**Direction:** ClientUI → GameData

**Semantics:** This UI file references this game system or data type.

**Search scenario:** "What UI shows the inventory?" → find gamedata node → reverse-traverse UI_BINDS_TO → ClientUI nodes.

**Extraction rules (pending verification):**
- Parse XAML files for `{Binding ...}` expressions
- Map binding path stems to game data types via a curated XAML_BINDING_MAP
- Requires empirical verification of XAML binding patterns before implementation

**Fallback:** If binding patterns don't reference gamedata entities, use filename keyword matching (e.g., "Inventory.xaml" → items, "HealthBar.xaml" → entity health).

---

### 3.4 Deprecation: RELATES_TO

The generic RELATES_TO edge type is **deprecated** and replaced by the typed edges above. On next gamedata re-index:
1. All RELATES_TO edges are deleted (already happens via the blanket DELETE)
2. New typed edges are inserted by per-type extraction functions
3. No migration needed — the re-index is the migration

---

## 4. Embedding Text Templates

### 4.1 Critical Fix: ITEM Inline Recipe

**Bug:** `GameDataTextBuilder.buildItemText()` (lines 50-112) never reads the `Recipe` field. Items with inline recipes (torches, benches, etc.) have crafting data silently dropped from the embedding text.

**Fix — append this block inside `buildItemText()` after the Stats section:**

```kotlin
// Inline Recipe — ensures "how to craft X" queries match this item
obj.obj("Recipe")?.let { recipe ->
    sb.appendLine("Crafting recipe:")
    sb.appendLine("  (This item can be crafted)")
    recipe.arr("BenchRequirement")?.let { benches ->
        val benchNames = benches.mapNotNull { it.jsonObjectOrNull()?.str("Id") }
        if (benchNames.isNotEmpty()) sb.appendLine("  Bench: ${benchNames.joinToString(", ")}")
    }
    recipe.arr("Input", "inputs", "ingredients")?.let { inputs ->
        inputs.take(5).mapNotNull { el ->
            val o = el.jsonObjectOrNull() ?: return@mapNotNull null
            val item = o.str("ItemId", "item", "id")
                ?: o.str("ResourceTypeId")?.let { "resource:$it" }
                ?: return@mapNotNull null
            val qty = o.int("Quantity", "quantity") ?: 1
            "  ingredient: $item x$qty"
        }.forEach { sb.appendLine(it) }
        if (inputs.size > 5) sb.appendLine("  ... and ${inputs.size - 5} more")
    }
    recipe.int("TimeSeconds")?.let { sb.appendLine("  Crafting time: ${it}s") }
    recipe.int("OutputQuantity", "Quantity")?.let { sb.appendLine("  Output quantity: $it") }
    sb.appendLine("  Keywords: crafting recipe ingredients how to make craft")
}
```

### 4.2 New Custom Builders (replacing generic fallback)

Seven data types currently use the generic fallback builder that only extracts flat primitive keys. Each needs a dedicated builder.

#### PROJECTILE

```
Projectile: {name}
Type: {Type}
Damage: {Damage}
Muzzle velocity: {MuzzleVelocity}
Time to live: {TimeToLive}s
Gravity: {Gravity}
Bounciness: {Bounciness}
Hit particle: {HitParticles.SystemId}
Death particle: {DeathParticles.SystemId}
Keywords: arrow bullet projectile ranged attack trajectory
File: {filePath}
```

Fields: `Damage` (flat int or nested `Damage.Value`), `MuzzleVelocity`, `TimeToLive`, `Gravity`, `Bounciness`, `HitParticles.SystemId`, `DeathParticles.SystemId`. Max: 400 chars.

#### WEATHER

```
Weather: {name}
Precipitation: {PrecipitationType or Precipitation.Type}
Particle system: {Particle.SystemId}
Tags: {Tags object — enumerate category:values}
Keywords: weather precipitation rain snow blizzard fog storm
File: {filePath}
```

Fields: `Particle.SystemId`, `Tags` (object with arrays like `{Snow: ["Storm"], Zone: ["Zone3"]}`). Max: 400 chars.

#### WORLDGEN

```
World gen: {name}
Type: {Type}
Noise type: {Noise.Type or NoiseType}
Fractal mode: {FractalMode}
Octaves: {Octaves}
Scale: {Scale}
Threshold: {Threshold}
Keywords: world generation noise terrain fractal procedural
File: {filePath}
```

Fields: Nested `Noise.{Type, FractalMode, Octaves, Scale}` or flat root-level equivalents. One level of sub-noise Types for composite noise. Max: 500 chars.

#### OBJECTIVE

```
Objective: {name}
Task types: {distinct TaskSets[].Tasks[].Type values}
Tasks:
  Kill: {NpcId} x{Count}
  Reach: {LocationId}
  Use: {EntityId}
Completions: {Completions[].Type}
Keywords: objective quest mission task kill bounty
File: {filePath}
```

Fields: `TaskSets[].Tasks[]` traversal extracting Type, NpcId, LocationId, Count. `Completions[].Type`. Max: 600 chars.

#### ENVIRONMENT

```
Environment: {name}
Type: {Type}
Gravity: {Physics.Gravity or Gravity}
Air resistance: {Physics.AirResistance}
Ambient light: {Lighting.AmbientLight}
Particle systems: {Particles[].SystemId}
Keywords: physics gravity lighting ambient environment
File: {filePath}
```

Fields: Nested `Physics.{Gravity, AirResistance}`, `Lighting.{AmbientLight}`, `Particles[].SystemId`. Max: 400 chars.

#### CAMERA

```
Camera: {name}
Mode: {Mode or CameraMode}
Field of view: {FOV}
Offset: x={Offset.X}, y={Offset.Y}, z={Offset.Z}
Min zoom: {MinZoom}  Max zoom: {MaxZoom}
Keywords: camera view perspective zoom offset FOV
File: {filePath}
```

Fields: `Mode/CameraMode`, `FOV/FieldOfView`, `Offset.{X,Y,Z}`, `MinZoom`, `MaxZoom`, `TransitionTime`. Max: 400 chars.

#### GAMEPLAY

```
Gameplay config: {name}
{key}: {value}                    (all root-level flat primitives)
{SectionName}:                    (one level of nested object descent)
  {subkey}: {subvalue}            (up to 5 per section)
Keywords: gameplay config balance setting parameter
File: {filePath}
```

Extends the generic builder with one level of object descent. Max: 600 chars.

### 4.3 Existing Builder Improvements

#### RECIPE — Add vocabulary injection

Append before `File:`:
```
Keywords: crafting recipe how to make {name} ingredients
```

#### NPC — Add conditional vocabulary

Append conditionally:
- If `Merchant=true`: `"(merchant, sells items)"`
- If `Hostile=true`: `"(hostile enemy)"`
- Always: `Keywords: enemy mob creature NPC character`

#### SHOP — Add vocabulary injection

Append: `Keywords: vendor trade exchange merchant buy sell barter`

### 4.4 CODE Corpus — Include Field Declarations

**Bug:** `MethodChunk.fields` (up to 20 field declarations per class) is stored but never written to `embeddingText`. The `buildString` block in JavaChunker doesn't reference `fields`.

**Fix:** Add top 5-8 field declarations to the embedding header:
```
// Package: combat.calculation       (strip com.hypixel.hytale. prefix)
// Class: DamageCalculator
// Fields: private int baseDamage, private float critMultiplier, ...
// Method: calculateDamage

{signature}
{body}
```

### 4.5 CLIENT Corpus — Add Purpose Label

Derive a human-readable purpose from PascalCase filename splitting:
```kotlin
"HotbarSlot" → "hotbar slot"  // 2+ words extracted
```

Add to embedding text:
```
XAML UI Template: HotbarSlot
Purpose: hotbar slot
Category: InGame
...
```

### 4.6 DOCS Corpus — Type-Specific Keywords

Add per-doc-type vocabulary injection:
- GUIDE: `Keywords: tutorial how to guide walkthrough`
- REFERENCE: `Keywords: API reference documentation method class`
- FAQ: `Keywords: FAQ troubleshoot common problem question answer`
- EXAMPLE: `Keywords: example sample code snippet demo`

### 4.7 Version Bump

Any change to builder logic requires incrementing `TEXT_BUILDER_VERSION` (currently 4) in `GameDataTextBuilder.kt` to trigger full re-indexing.

---

## 5. Metadata Fields

Metadata fields are stored in dedicated SQL columns (not the JSON `metadata` blob) for indexed filtering.

### 5.1 Gamedata Metadata (new columns on `nodes` table)

| Column | Type | Applies To | Purpose |
|--------|------|-----------|---------|
| `quality` | TEXT | item | Common/Rare/Epic/Legendary |
| `has_inline_recipe` | INTEGER | item | 0/1 — enables "show craftable items" |
| `hostile` | INTEGER | npc, entity | 0/1 |
| `merchant` | INTEGER | npc | 0/1 |
| `faction` | TEXT | npc, npc_group | Faction name |
| `tags_json` | TEXT | item, block, prefab | JSON array of tags |
| `categories_json` | TEXT | item | JSON array of category path |

### 5.2 Implementation Note

Rather than adding many nullable columns to the nodes table, store these in the existing `metadata` TEXT column as JSON. Use `json_extract()` for queries:

```sql
SELECT * FROM nodes
WHERE corpus = 'gamedata' AND data_type = 'item'
  AND json_extract(metadata, '$.has_inline_recipe') = 1
```

This avoids schema migration for metadata additions. Only add dedicated columns for fields that are frequently filtered in WHERE clauses AND appear in >1000 nodes.

---

## 6. ID Resolution Strategy

### 6.1 Current Problem

`extractRelatedIds` uses case-insensitive `display_name` matching, which is brittle:
- Different files can share a display name (e.g., "Default")
- Display names may be transformed (underscores removed, camelCase converted)

### 6.2 Solution: Filename Stem Matching

Game data JSON references consistently use filename stems as identifiers:
- Recipe `Input[i].ItemId = "Ingredient_Bar_Copper"` → matches file `Ingredient_Bar_Copper.json`
- NPC `DropList = "Drop_Goblin_Warrior"` → matches file `Drop_Goblin_Warrior.json`

**New resolution approach:**

```kotlin
fun buildStemLookup(db: KnowledgeDatabase): Map<String, List<String>> {
    // display_name IS the filename stem (set by GameDataParser.deriveName())
    return db.query(
        "SELECT id, display_name FROM nodes WHERE corpus = 'gamedata'"
    ).groupBy(
        keySelector = { it.getString("display_name").lowercase() },
        valueTransform = { it.getString("id") }
    )
}
```

**Multi-match handling:** When a stem maps to multiple node IDs (e.g., "Default" in different directories), create edges to all matches but flag with metadata `{"multi_match": true}`. Graph traversal can de-prioritize multi-match edges when single-match alternatives exist.

### 6.3 ID Format Table

| Corpus | ID Format | Stability |
|--------|-----------|-----------|
| Code - Method | `method:com.foo.Bar#baz` | FQCN-stable (decompiled code doesn't refactor) |
| Code - Class | `class:com.foo.Bar` | FQCN-stable |
| Code - Package | `pkg:com.foo` | Package-stable |
| Gamedata | `gamedata:Server:Item:Items:Torch.json` | ZIP-path-stable |
| Client | `xaml:path/file.xaml` / `ui:path/file.ui` | Relative-path-stable |
| Docs | `docs:relative/path.md` | Path-stable |
| Virtual | `virtual:{type}:{id}` | Synthetic, ephemeral |

---

## 7. Search Integration

### 7.1 QueryRouter Extensions

New regex patterns for directed graph queries:

```kotlin
// Recipe output lookup: "how to craft X", "how to make X"
private val CRAFT_PATTERN = Regex(
    """(?:how|what)\s+(?:to\s+)?(?:craft|make|produce|create)\s+(\w+)""",
    RegexOption.IGNORE_CASE
)
// → RouteResult(HYBRID, entityName, "REQUIRES_ITEM")

// Drop source lookup: "what drops from X", "X drops"
private val DROP_FROM_PATTERN = Regex(
    """(?:what|which)\s+(?:drops?|loot)\s+(?:from|by)\s+(\w+)""",
    RegexOption.IGNORE_CASE
)
// → RouteResult(GRAPH, entityName, "DROPS_ON_DEATH")

// Recipe input lookup: "what uses X", "recipes with X"
private val USES_ITEM_PATTERN = Regex(
    """(?:what|which)\s+(?:uses?|requires?|needs?)\s+(\w+)""",
    RegexOption.IGNORE_CASE
)
// → RouteResult(GRAPH, entityName, "REQUIRES_ITEM")

// Shop lookup: "where to buy X", "who sells X"
private val BUY_PATTERN = Regex(
    """(?:where|who)\s+(?:to\s+)?(?:buy|sell|trade)\s+(\w+)""",
    RegexOption.IGNORE_CASE
)
// → RouteResult(GRAPH, entityName, "OFFERED_IN_SHOP")
```

### 7.2 Graph Traversal Extensions

New methods in `GraphTraversal.kt`:

```kotlin
fun findRecipeInputs(itemNodeId: String, limit: Int): List<SearchResult>
    // Forward: item → REQUIRES_ITEM → ingredient items
    // Reverse: item ← PRODUCES_ITEM ← recipes

fun findDropsFrom(entityNodeId: String, limit: Int): List<SearchResult>
    // Forward: entity → DROPS_ON_DEATH → drop tables
    // Then:    drop → DROPS_ITEM → items

fun findShopsSellingItem(itemNodeId: String, limit: Int): List<SearchResult>
    // Reverse: item ← OFFERED_IN_SHOP ← shops

fun findGroupMembers(groupNodeId: String, limit: Int): List<SearchResult>
    // Forward: group → HAS_MEMBER → NPCs
```

### 7.3 expandCrossCorpus Updates

The `when (seed.corpus)` block in `KnowledgeSearchService.expandCrossCorpus()` currently only handles "gamedata" and "code" arms. Add:

```kotlin
"client" -> {
    // Expand client UI results to related gamedata via UI_BINDS_TO
    if (gamedataEnabled) { /* traverse UI_BINDS_TO edges */ }
}
"docs" -> {
    // Expand docs results to related code and gamedata
    if (codeEnabled) { /* traverse DOCS_REFERENCES → class nodes */ }
    if (gamedataEnabled) { /* traverse DOCS_REFERENCES → gamedata nodes */ }
}
```

### 7.4 Gamedata Intent Detection

Update `GAMEDATA_INTENT_RULES` for directed patterns:

```kotlin
// Crafting: search both recipe files AND items with inline recipes
Regex("\\b(craft|recipe|crafting|bench|smelt|cook|brew)\\b", IGNORE_CASE)
    to setOf("recipe", "item"),

// Drops: search drop tables specifically
Regex("\\b(drop|drops|loot)\\s+from\\b", IGNORE_CASE)
    to setOf("drop"),
```

---

## 8. Migration Plan

### 8.1 Schema V3 (Additive Only)

No destructive schema changes. All new columns and indexes are additive.

```sql
-- The edges table already has target_resolved and metadata columns.
-- No schema migration needed — just new edge_type values.

-- Optional: index on edge_type for typed traversal performance
CREATE INDEX IF NOT EXISTS idx_edges_type ON edges(edge_type);
CREATE INDEX IF NOT EXISTS idx_edges_target_type ON edges(target_id, edge_type);
```

### 8.2 Re-Index Trigger

Bump `TEXT_BUILDER_VERSION` from 4 to 5. This causes `GameDataIndexerTask` to:
1. Delete all gamedata nodes, edges, and file hashes (lines 71-74)
2. Re-parse all gamedata files
3. Rebuild embedding text with the new templates
4. Rebuild all edges with typed extraction

### 8.3 Edge Build Refactor

Replace the current `buildGameDataEdges()` method (which iterates `relatedIds` and creates generic RELATES_TO) with per-type extraction functions:

```kotlin
private fun buildGameDataEdges(db: KnowledgeDatabase, chunks: List<GameDataChunk>) {
    // Scoped delete — protects cross-corpus edges
    db.execute("""
        DELETE FROM edges
        WHERE (source_id LIKE 'gamedata:%' OR target_id LIKE 'gamedata:%')
          AND edge_type IN ('RELATES_TO', 'IMPLEMENTED_BY',
                            'REQUIRES_ITEM', 'PRODUCES_ITEM', ...)
    """)

    val stemLookup = buildStemLookup(db)
    val allEdges = mutableListOf<EdgeRow>()

    for (chunk in chunks) {
        val json = parseJson(chunk.rawJson) ?: continue
        when (chunk.type) {
            ITEM    -> allEdges += extractItemEdges(chunk, json, stemLookup)
            RECIPE  -> allEdges += extractRecipeEdges(chunk, json, stemLookup)
            DROP    -> allEdges += extractDropEdges(chunk, json, stemLookup)
            NPC     -> allEdges += extractNpcEdges(chunk, json, stemLookup)
            SHOP    -> allEdges += extractShopEdges(chunk, json, stemLookup)
            NPC_GROUP -> allEdges += extractGroupEdges(chunk, json, stemLookup)
            // ... per-type extractors
            else    -> {} // Types without edges
        }
    }

    insertEdges(db, allEdges)
    buildImplementedByEdges(db, chunks) // Existing IMPLEMENTED_BY logic
}
```

### 8.4 Indexer Ordering

Edge building depends on prior corpus nodes existing. Enforce ordering:
1. **Code corpus** (nodes + code edges)
2. **Gamedata corpus** (nodes + gamedata edges + IMPLEMENTED_BY edges)
3. **Client corpus** (nodes + UI_BINDS_TO edges)
4. **Docs corpus** (nodes + DOCS_REFERENCES edges)

Currently `BuildAllIndexAction` runs all 4 in parallel. Phase 2 should add dependency ordering for edge-building phases.

---

## 9. Implementation Priorities

### Wave 1 — Fix Search Failures (highest impact, implement first)

| Task | Files | Impact |
|------|-------|--------|
| ITEM inline Recipe in embedding text | `GameDataTextBuilder.kt` | Fixes "how to craft torches" returning nothing |
| REQUIRES_ITEM edge extraction | `GameDataIndexerTask.kt` | Enables graph traversal for ingredients |
| PRODUCES_ITEM edge extraction | `GameDataIndexerTask.kt` | Enables "what recipes make X?" |
| DROPS_ON_DEATH + DROPS_ITEM edges | `GameDataIndexerTask.kt` | Enables "what drops from goblins?" |
| Scoped edge DELETE fix | `GameDataIndexerTask.kt` line 237 + 73 | Prevents destroying cross-corpus edges |
| Stem-based ID resolution | `GameDataIndexerTask.kt` | Replaces brittle display_name matching |
| TEXT_BUILDER_VERSION bump | `GameDataTextBuilder.kt` | Triggers full re-index |

### Wave 2 — Custom Builders + More Edges

| Task | Files | Impact |
|------|-------|--------|
| 7 custom text builders | `GameDataTextBuilder.kt` | Fixes projectile/weather/worldgen/etc. search |
| OFFERED_IN_SHOP edges | `GameDataIndexerTask.kt` | Shop lookup queries |
| HAS_MEMBER / BELONGS_TO_GROUP edges | `GameDataIndexerTask.kt` | NPC group queries |
| REQUIRES_BENCH virtual edges | `GameDataIndexerTask.kt` | Bench-filtered recipe queries |
| QueryRouter new patterns | `QueryRouter.kt` | Routes directed queries to graph |
| GraphTraversal new methods | `GraphTraversal.kt` | Traversal for new edge types |

### Wave 3 — Cross-Corpus + Polish

| Task | Files | Impact |
|------|-------|--------|
| DOCS_REFERENCES edges | `DocsIndexerTask.kt` | Docs ↔ code linking |
| SPAWNS_PARTICLE virtual edges | `GameDataIndexerTask.kt` | Particle system queries |
| Code field declarations in embedding | `JavaChunker.kt` | Better code search |
| NPC/RECIPE/SHOP vocabulary injection | `GameDataTextBuilder.kt` | Better vector matching |
| Client UI purpose labels | `ClientUIParser.kt` | Better UI file search |
| Indexer dependency ordering | `BuildAllIndexAction.kt` | Correct edge build order |
| UI_BINDS_TO edges (if XAML verification passes) | `ClientUIIndexerTask.kt` | UI ↔ gamedata linking |

---

## Appendix A: Edge Type Quick Reference

| Edge Type | Source → Target | Priority |
|-----------|----------------|----------|
| REQUIRES_ITEM | recipe/item → item | P1 |
| PRODUCES_ITEM | recipe → item | P1 |
| DROPS_ITEM | drop → item | P1 |
| DROPS_ON_DEATH | npc/entity/block → drop | P1 |
| OFFERED_IN_SHOP | shop → item | P1 |
| HAS_MEMBER | npc_group → npc | P1 |
| BELONGS_TO_GROUP | npc → npc_group | P2 |
| TARGETS_GROUP | npc/objective/farming → npc_group | P2 |
| REQUIRES_BENCH | recipe/item → virtual:bench | P2 |
| SPAWNS_PARTICLE | various → virtual:particle | P3 |
| APPLIES_EFFECT | interaction → virtual:effect | P3 |
| REFERENCES_WORLDGEN | zone → worldgen | P3 |
| IMPLEMENTED_BY | gamedata → class | Existing |
| DOCS_REFERENCES | docs → class/gamedata | P3 |
| UI_BINDS_TO | client → gamedata | P3 |
| EXTENDS | class → class | Existing |
| IMPLEMENTS | class → class | Existing |
| CONTAINS | package → class | Existing |

## Appendix B: Alternatives Considered

### Tagged RELATES_TO Instead of Typed Edges

An alternative approach: keep a single RELATES_TO edge type but expand field extraction coverage and store the field name as edge metadata. Queries would use `WHERE edge_type='RELATES_TO' AND json_extract(metadata,'$.field')='Input'`.

**Why not adopted:**
1. Directed traversal requires edge typing. "What does this recipe require?" vs "what does this recipe produce?" cannot be distinguished by field name alone (both are item references from a recipe).
2. `json_extract()` is not indexed in SQLite — every traversal becomes a full scan of the edges table.
3. QueryRouter pattern matching maps naturally to edge type names, not metadata field values.

**When this alternative would be correct:** If implementation resources are severely constrained and only 2-3 edge types are needed. For the full 14-type taxonomy, typed edges are the right abstraction.
