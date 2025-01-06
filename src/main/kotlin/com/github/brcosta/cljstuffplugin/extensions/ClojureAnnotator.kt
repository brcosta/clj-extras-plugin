package com.github.brcosta.cljstuffplugin.extensions

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.parentOfType
import cursive.ClojureLanguage
import cursive.psi.api.ClKeyword
import cursive.psi.api.ClList
import cursive.psi.impl.ClSexpComment

@Suppress("UnstableApiUsage")
class ClojureAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.language != ClojureLanguage.getInstance()) {
            return
        }

        try {
            when {
                element is ClKeyword -> {
                    if (element.namespace != null) {
                        val startOffset = element.textRange.startOffset
                        if (isNamespaced(element)) {
                            val nsLength = element.text.indexOf('/')
                            val prefixRange = TextRange.from(startOffset, nsLength)
                            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                .range(prefixRange).textAttributes(ClojureColorsAndFontsPageEx.KEYWORD_NAMESPACE)
                                .create()
                        } else {
                            val range = TextRange.from(startOffset, element.text.length)
                            val qualifiedName = element.qualifiedName
                            holder.newAnnotation(HighlightSeverity.INFORMATION, qualifiedName)
                                .range(range)
                                .afterEndOfLine()
                                .tooltip(qualifiedName)
                                .create()
                        }
                    }
                }
                isNsReaderMacro(element) -> {
                    val range = TextRange.from(element.textRange.startOffset, element.text.length)
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(range).textAttributes(ClojureColorsAndFontsPageEx.KEYWORD_NAMESPACE)
                        .create()
                }
                isReaderNamespacedSymbol(element) -> {
                    val range = TextRange.from(element.textRange.startOffset, element.text.length)

                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(range)
                        .textAttributes(
                            when (element.parent?.parent) {
                                is ClList -> ClojureColorsAndFontsPageEx.HEAD_SYMBOL_NAMESPACE
                                else -> ClojureColorsAndFontsPageEx.SYMBOL_NAMESPACE
                            }
                        )
                        .create()
                }
            }
        } catch (e: Exception) {
            // should not happen
        }
    }

    private fun isNamespaced(element: ClKeyword) =
        (element.text?.contains("/") == true) &&
                (element.parentOfType<ClSexpComment>(false) == null)

    private fun isNsReaderMacro(element: PsiElement) =
        (element.prevSibling != null) &&
                (element.prevSibling.text == "#:") &&
                (element.parentOfType<ClSexpComment>(false) == null)

    private fun isReaderNamespacedSymbol(element: PsiElement) =
        (element.nextSibling != null) &&
                (element.nextSibling is LeafPsiElement) &&
                ((element.nextSibling as LeafPsiElement).elementType.toString() == "ns separator") &&
                (element.parentOfType<ClSexpComment>(false) == null)
}
