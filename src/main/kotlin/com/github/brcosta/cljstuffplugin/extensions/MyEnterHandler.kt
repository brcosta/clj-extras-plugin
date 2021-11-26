package com.github.brcosta.cljstuffplugin.extensions

import com.github.brcosta.cljstuffplugin.actions.EvaluateInlineBaseAction
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull

@Suppress("unused")
class MyEnterHandler : EnterHandlerDelegateAdapter() {

    override fun preprocessEnter(
        file: PsiFile,
        editor: Editor,
        caretOffset: Ref<Int>,
        caretAdvance: Ref<Int>,
        dataContext: DataContext,
        originalHandler: EditorActionHandler?
    ): EnterHandlerDelegate.Result {
        clearEditorInlays(editor)
        return super.preprocessEnter(file, editor, caretOffset, caretAdvance, dataContext, originalHandler)
    }

    private fun clearEditorInlays(@NotNull editor: Editor) {
        editor.inlayModel.getAfterLineEndElementsInRange(
            0,
            editor.document.textLength,
            EvaluateInlineBaseAction.TextLabelCustomElementRenderer::class.java
        ).forEach(Disposer::dispose)
    }

}
