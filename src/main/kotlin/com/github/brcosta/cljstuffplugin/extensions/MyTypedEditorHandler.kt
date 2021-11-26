package com.github.brcosta.cljstuffplugin.extensions

import com.github.brcosta.cljstuffplugin.actions.EvaluateInlineBaseAction.TextLabelCustomElementRenderer
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull

class MyTypedEditorHandler : TypedHandlerDelegate() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        val runnable = Runnable { clearEditorInlays(editor) }
        WriteCommandAction.runWriteCommandAction(project, runnable)
        return Result.CONTINUE
    }

    private fun clearEditorInlays(@NotNull editor: Editor) {
        editor.inlayModel.getAfterLineEndElementsInRange(
            0, editor.document.textLength,
            TextLabelCustomElementRenderer::class.java
        ).forEach(Disposer::dispose)
    }
}
