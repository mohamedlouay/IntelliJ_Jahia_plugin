package fr.tolc.jahia.intellij.plugin.cnd.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import fr.tolc.jahia.intellij.plugin.cnd.dialogs.CreateCndFileDialog;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndFileTemplateUtil;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndProjectFilesUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.IOException;

public class NewCndFileAction extends AnAction {

    /**
     * BGT is mandatory here: update() calls getJahiaMetaInfFolderPath, which walks the module
     * model and the VFS -- far too slow for the EDT.
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                CreateCndFileDialog dialog = new CreateCndFileDialog(e.getProject());
                dialog.setVisible(true);

                if (dialog.isOkClicked()) {
                    String fileName = dialog.getCndFileName();
                    if (!StringUtil.isEmptyOrSpaces(fileName)) {
                        Project project = e.getProject();
                        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
                        if (virtualFile != null && project != null) {
                            // The action can be invoked on a file as well as on a folder; the new
                            // .cnd belongs next to it either way.
                            VirtualFile directory = virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
                            if (directory != null) {
                                createCndFile(project, directory, fileName);
                            }
                        }
                    }
                }
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean showAction = false;
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile != null && project != null) {
            String metaInfFolderPath = CndProjectFilesUtil.getJahiaMetaInfFolderPath(e.getProject(), virtualFile);
            if (!StringUtil.isEmptyOrSpaces(metaInfFolderPath)) {
                showAction = virtualFile.getPath().contains(metaInfFolderPath);
            }
        }
        e.getPresentation().setEnabledAndVisible(showAction);
    }

    private void createCndFile(final Project project, final VirtualFile directory, final String cndFileName) {
        final String realFileName = cndFileName.endsWith(".cnd") ? cndFileName : cndFileName + ".cnd";

        // Created through the VFS inside a write action, from the template bundled in the jar.
        // The previous mkdirs + synchronous refresh + Files.copy went behind the IDE's back: the
        // file only became visible at the next refresh.
        final VirtualFile cndVirtualFile;
        try {
            cndVirtualFile = WriteCommandAction.writeCommandAction(project)
                    .withName("Create CND File")
                    .compute(() -> CndFileTemplateUtil.createIfMissing(
                            this, directory, realFileName,
                            CndFileTemplateUtil.read(CndFileTemplateUtil.CND_FILE)));
        } catch (IOException e) {
            throw new IncorrectOperationException(e);
        }

        //Open new file in editor
        FileEditorManager.getInstance(project).openFile(cndVirtualFile, true);

        //Expand folder in Project view
        ProjectView.getInstance(project).select(null, cndVirtualFile, false);

        //Caret at the end of the file
        ((Navigatable) PsiManager.getInstance(project).findFile(cndVirtualFile).getLastChild().getNavigationElement()).navigate(true);
        CaretModel caretModel = FileEditorManager.getInstance(project).getSelectedTextEditor().getCaretModel();
        caretModel.moveCaretRelatively(-caretModel.getLogicalPosition().column, 2, false, false, false);
    }
}
