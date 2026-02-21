package com.hyve.mod

import com.intellij.spellchecker.BundledDictionaryProvider

class HytaleBundledDictionaryProvider : BundledDictionaryProvider {
    override fun getBundledDictionaries(): Array<String> = arrayOf("/spellchecker/hytale.dic")
}
