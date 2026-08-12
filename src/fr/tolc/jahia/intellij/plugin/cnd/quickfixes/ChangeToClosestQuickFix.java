package fr.tolc.jahia.intellij.plugin.cnd.quickfixes;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ChangeToClosestQuickFix extends BaseIntentionAction {

    private PsiElement element;
    private String[] options;

    public ChangeToClosestQuickFix(PsiElement element, String[] options) {
        this.element = element;
        this.options = options;
    }

    @NotNull
    @Override
    public String getText() {
        return "Change to";
    }
    
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "Cnd";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                //TODO: change element text to closest one
                // The former getClosest() helper computed a Levenshtein distance via
                // commons-lang and assigned it to a local that was never read, so it was
                // removed with commons-lang rather than ported. Reinstate it with
                // com.intellij.openapi.util.text.StringUtil#difference or commons-text
                // if this quick fix is ever implemented.
//               element.getTextRange().
            }
        });
    }
}
