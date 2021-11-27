package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import cursive.ClojureIcons
import cursive.highlighter.ClojureSyntaxHighlighter
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@Suppress("unused")
class ClojureColorsAndFontsPageEx : ColorSettingsPage {

    override fun getDisplayName(): String {
        return "Clojure Stuff"
    }

    override fun getIcon(): Icon? {
        return ClojureIcons.CLOJURE
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return ATTRS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return arrayOf()
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return ClojureSyntaxHighlighter()
    }

    override fun getDemoText(): @NonNls String {
        return "; No preview available"
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> {
        val map: MutableMap<String, TextAttributesKey> = mutableMapOf()
        map["keyword-namespace"] = KEYWORD_NAMESPACE
        map["symbol-namespace"] = SYMBOL_NAMESPACE
        return map
    }

    companion object {
        private val ATTRS: Array<AttributesDescriptor>
        val KEYWORD_NAMESPACE = TextAttributesKey.createTextAttributesKey("Keyword Namespace")
        val SYMBOL_NAMESPACE = TextAttributesKey.createTextAttributesKey("Symbol Namespace")

        init {
            ATTRS = arrayOf(
                AttributesDescriptor("Keyword Namespace", KEYWORD_NAMESPACE),
                AttributesDescriptor("Symbol Namespace", SYMBOL_NAMESPACE)
            )
        }
    }
}
