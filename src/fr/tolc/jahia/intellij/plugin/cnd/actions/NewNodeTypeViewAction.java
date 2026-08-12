package fr.tolc.jahia.intellij.plugin.cnd.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import fr.tolc.jahia.intellij.plugin.cnd.model.NodeTypeModel;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndNodeType;
import fr.tolc.jahia.intellij.plugin.cnd.quickfixes.CreateNodeTypeViewQuickFix;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndProjectFilesUtil;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.File;

import static fr.tolc.jahia.intellij.plugin.cnd.utils.CndProjectFilesUtil.getModuleForFile;

public class NewNodeTypeViewAction extends AnAction {

    /**
     * BGT is mandatory here: update() goes through getNodeType, which queries the module model
     * and resolves node types through FileTypeIndex.
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        CndNodeType cndNodeType = getNodeType(e);
        if (cndNodeType != null) {
            Project project = e.getProject();
            VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
            new CreateNodeTypeViewQuickFix((virtualFile != null)? CndProjectFilesUtil.getModuleForFile(project, virtualFile) : null, cndNodeType)
                    .invoke(project, e.getData(CommonDataKeys.EDITOR), e.getData(CommonDataKeys.PSI_FILE));
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean showAction = false;
        CndNodeType cndNodeType = getNodeType(e);
        if (cndNodeType != null) {
            showAction = true;
        }
        e.getPresentation().setEnabledAndVisible(showAction);
    }

    private CndNodeType getNodeType(AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile != null && project != null) {
            String jahiaWorkFolderPath = CndProjectFilesUtil.getJahiaWorkFolderPath(getModuleForFile(project, virtualFile));
            if (!StringUtil.isEmptyOrSpaces(jahiaWorkFolderPath) && virtualFile.getPath().contains(jahiaWorkFolderPath)) {
                NodeTypeModel nodeTypeModel = null;
                try {
                    nodeTypeModel = new NodeTypeModel(virtualFile.getName(), true);
                } catch (IllegalArgumentException ex) {
                    //Nothing to do
                }

                // Non-refreshing lookup on purpose. This runs from update(), which on BGT holds a
                // read lock, and a synchronous VFS refresh under a read lock is forbidden. The work
                // folder belongs to the project, so the VFS already knows about it.
                VirtualFile jahiaWorkFolderFile = LocalFileSystem.getInstance().findFileByIoFile(new File(jahiaWorkFolderPath));
                VirtualFile parentDir = virtualFile;
                while (nodeTypeModel == null && parentDir != null && jahiaWorkFolderFile != null && !parentDir.equals(jahiaWorkFolderFile)) {
                    //Try with parent directory (because of IntelliJ's weird way of merging directories into one if only one subdirectory)
                    parentDir = parentDir.getParent();
                    try {
                        nodeTypeModel = new NodeTypeModel(parentDir.getName(), true);
                    } catch (IllegalArgumentException ex) {
                        //Nothing to do
                    }

                }

                if (nodeTypeModel != null) {
                    return CndUtil.findNodeType(project, nodeTypeModel);
                }
            }
        }
        return null;
    }
}
