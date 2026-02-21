// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

/**
 * Determines whether a Word Bank item can be dropped onto a property slot.
 *
 * Rules are checked in priority order (spec 07, FR-3):
 * 1. Style kind → style slot type → Accept
 * 2. Exact type match → Accept
 * 3. Number item → percent slot → Accept
 * 4. Localization kind → text slot → Accept
 * 5. Asset kind → image slot → Accept
 * 6. Asset kind → font slot → Accept
 * 7. Everything else → Reject
 *
 * This function is pure and cheap to call — it runs on every pointer-move
 * event during a drag operation.
 */
fun canDrop(item: WordBankItem, slot: PropertySlot): Boolean {
    if (item.kind == WordBankKind.STYLE && slot.type == ComposerPropertyType.STYLE) return true
    if (item.type == slot.type) return true
    if (item.type == ComposerPropertyType.NUMBER && slot.type == ComposerPropertyType.PERCENT) return true
    if (item.kind == WordBankKind.LOCALIZATION && slot.type == ComposerPropertyType.TEXT) return true
    if (item.kind == WordBankKind.ASSET && slot.type == ComposerPropertyType.IMAGE) return true
    if (item.kind == WordBankKind.ASSET && slot.type == ComposerPropertyType.FONT) return true
    return false
}

/**
 * Determines the fill mode and value to assign when a Word Bank item
 * is dropped onto a compatible property slot (spec 07, FR-5).
 *
 * @return Pair of (FillMode, value string)
 */
fun fillModeForDrop(item: WordBankItem): Pair<FillMode, String> = when (item.kind) {
    WordBankKind.VARIABLE -> FillMode.VARIABLE to item.name
    WordBankKind.STYLE -> FillMode.VARIABLE to item.name
    WordBankKind.IMPORT -> FillMode.IMPORT to item.name
    WordBankKind.LOCALIZATION -> FillMode.LOCALIZATION to item.name
    WordBankKind.ASSET -> FillMode.LITERAL to item.name
}
