package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.codeHighlighting.RainbowHighlighter
import com.intellij.lang.Language
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.RainbowColorSettingsPage
import cursive.ClojureIcons
import cursive.ClojureLanguage
import cursive.highlighter.ClojureSyntaxHighlighter
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@Suppress("unused")
class ClojureColorsAndFontsPageEx : RainbowColorSettingsPage {

    override fun getDisplayName(): String {
        return "Clojure Extras"
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
        val map = RainbowHighlighter.createRainbowHLM()
        map["keyword-namespace"] = KEYWORD_NAMESPACE
        map["symbol-namespace"] = SYMBOL_NAMESPACE
        map["head-symbol-namespace"] = HEAD_SYMBOL_NAMESPACE
        return map
    }

    override fun isRainbowType(type: TextAttributesKey?): Boolean {
        return type == SEMANTIC_HIGHLIGHTING
    }

    override fun getLanguage(): Language? {
        return ClojureLanguage.getInstance()
    }

    companion object {
        private val ATTRS: Array<AttributesDescriptor>
        val KEYWORD_NAMESPACE = TextAttributesKey.createTextAttributesKey("Keyword Namespace")
        val SYMBOL_NAMESPACE = TextAttributesKey.createTextAttributesKey("Symbol Namespace")
        val HEAD_SYMBOL_NAMESPACE = TextAttributesKey.createTextAttributesKey("Head Symbol Namespace")
        val SEMANTIC_HIGHLIGHTING = TextAttributesKey.createTextAttributesKey("Semantic Highlighting")

        init {
            ATTRS = arrayOf(
                AttributesDescriptor("Keyword Namespace", KEYWORD_NAMESPACE),
                AttributesDescriptor("Symbol Namespace", SYMBOL_NAMESPACE),
                AttributesDescriptor("Head Symbol Namespace", HEAD_SYMBOL_NAMESPACE)
            )
        }
    }
}
