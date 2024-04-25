package com.github.brcosta.cljstuffplugin.extensions.codeInsight.daemon

import com.github.brcosta.cljstuffplugin.extensions.ClojureAnnotator.Companion.isNamespaced
import com.github.brcosta.cljstuffplugin.extensions.ClojureColorsAndFontsPageEx.Companion.SEMANTIC_HIGHLIGHTING
import com.intellij.codeInsight.daemon.RainbowVisitor
import com.intellij.codeInsight.daemon.UsedColors
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import cursive.psi.api.ClKeyword
import cursive.psi.api.ClojureFile
import cursive.psi.api.symbols.ClSymbol

/**
 * Enables semantic highlighting for all symbols and keywords. Namespaces are treated as separate highlighted elements.
 */
class ClojureRainbowVisitor : RainbowVisitor() {
    override fun suitableForFile(file: PsiFile): Boolean {
        return file is ClojureFile
    }

    override fun visit(element: PsiElement) {
        val maybeName = when (element) {
            is ClSymbol -> element.name
            is ClKeyword -> element.name
            else -> null
        }
        maybeName?.let { elementName ->
            element.context?.let { context ->
                fun separateNsHighlighting(keyword: ClKeyword): List<HighlightInfo> {
                    fun rangedInfo(name: String, start: Int, end: Int): HighlightInfo {
                        val color =
                            UsedColors.getOrAddColorIndex((context as UserDataHolderEx), name, highlighter.colorsCount)
                        return highlighter.getInfo(color, start, end, SEMANTIC_HIGHLIGHTING)
                    }

                    val start = keyword.textRange.startOffset
                    val nsEnd = start + keyword.text.indexOf('/')
                    return listOf(
                        rangedInfo(
                            keyword.namespace,
                            start,
                            nsEnd,
                        ),
                        rangedInfo(
                            elementName,
                            nsEnd,
                            keyword.textRange.endOffset,
                        )
                    )
                }

                if (element is ClKeyword && isNamespaced(element))
                    separateNsHighlighting(element).forEach(::addInfo)
                else
                    // separate ns highlighting for symbols works by default
                    addInfo(
                        getInfo(
                            context,
                            element,
                            elementName,
                            SEMANTIC_HIGHLIGHTING
                        )
                    )

            }
        }
    }

    override fun clone(): HighlightVisitor {
        return ClojureRainbowVisitor()
    }

}