package ai.opencode.ide.jetbrains.diff

import ai.opencode.ide.jetbrains.api.models.DiffEntry
import ai.opencode.ide.jetbrains.session.SessionManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages

private fun findEntryIndex(entries: List<DiffEntry>, filePath: String): Int {
    val idx = entries.indexOfFirst { it.file == filePath }
    return if (idx >= 0) idx else 0
}

private fun closeCurrentDiffIfPossible(e: AnActionEvent) {
    val project = e.project ?: return

    // The diff view is usually represented by a virtual file.
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    
    // DiffVirtualFile is usually temporary; closing a regular editor file is also reasonable here.
    FileEditorManager.getInstance(project).closeFile(virtualFile)
}

private fun openNextDiff(project: com.intellij.openapi.project.Project, previousIndex: Int, closeEvent: AnActionEvent?) {
    // Close the current window before opening another diff, if any remain.
    if (closeEvent != null) {
        closeCurrentDiffIfPossible(closeEvent)
    }

    val sessionManager = project.service<SessionManager>()
    val remaining = sessionManager.getAllDiffEntries()

    if (remaining.isEmpty()) {
        return
    }

    // Open the next diff when entries remain.
    val nextIndex = previousIndex.coerceIn(0, remaining.lastIndex)
    
    // Wait until the close operation completes before opening the next window.
    ApplicationManager.getApplication().invokeLater {
        project.service<DiffViewerService>().showMultiFileDiff(remaining, nextIndex)
    }
}

class AcceptFromDiffEditorAction(private val filePath: String) : AnAction() {
    init {
        templatePresentation.text = "Accept"
        templatePresentation.description = "Accept this change (git add)"
        templatePresentation.icon = AllIcons.Actions.Checked
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sessionManager = project.service<SessionManager>()

        val before = sessionManager.getAllDiffEntries()
        val previousIndex = findEntryIndex(before, filePath)

        val entry = sessionManager.getDiffForFile(filePath)
        if (entry != null) {
            // Navigate after the accept operation completes.
            sessionManager.acceptDiff(entry) { success, error ->
                if (success) {
                    openNextDiff(project, previousIndex, e)
                } else {
                    val details = error?.let { "\n\n$it" }.orEmpty()
                    Messages.showWarningDialog(project, "Failed to stage $filePath$details", "Accept Failed")
                }
            }
        } else {
            ApplicationManager.getApplication().invokeLater {
                Messages.showWarningDialog(project, "Diff entry not found for $filePath", "Accept Failed")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val enabled = project != null && project.service<SessionManager>().getDiffForFile(filePath) != null
        e.presentation.isEnabled = enabled
    }
}

class RejectFromDiffEditorAction(private val filePath: String) : AnAction() {
    init {
        templatePresentation.text = "Reject"
        templatePresentation.description = "Reject this change (restore original)"
        templatePresentation.icon = AllIcons.Actions.Rollback
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val sessionManager = project.service<SessionManager>()
        val entry: DiffEntry = sessionManager.getDiffForFile(filePath) ?: return

        // Build the confirmation message based on the file state.
        val isNewFile = entry.isNewFile
        val hasUserEdits = entry.hasUserEdits
        
        val message = buildString {
            if (isNewFile) {
                append("Delete new file '${entry.file}'?\n\n")
                append("This file was created by OpenCode and will be removed.")
            } else {
                append("Reject changes to '${entry.file}'?\n\n")
                append("The file will be restored to its state before OpenCode modified it.")
            }
            
            // Warn if the user also edited this file.
            if (hasUserEdits) {
                append("\n\n")
                append("WARNING: You have also edited this file. Your changes will be lost!")
            }
        }

        val confirm = Messages.showYesNoDialog(
            project,
            message,
            if (hasUserEdits) "Confirm Reject (Data Loss Warning)" else "Confirm Reject",
            Messages.getQuestionIcon()
        )

        if (confirm != Messages.YES) return

        val before = sessionManager.getAllDiffEntries()
        val previousIndex = findEntryIndex(before, filePath)

        // Navigate after the reject operation completes.
        sessionManager.rejectDiff(entry) { success ->
            if (success) {
                openNextDiff(project, previousIndex, e)
            } else {
                Messages.showWarningDialog(project, "Failed to restore $filePath", "Reject Failed")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val entry = if (project != null) project.service<SessionManager>().getDiffForFile(filePath) else null
        e.presentation.isEnabled = entry != null
    }
}
