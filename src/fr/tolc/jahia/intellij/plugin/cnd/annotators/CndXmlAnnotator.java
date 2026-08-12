package fr.tolc.jahia.intellij.plugin.cnd.annotators;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class CndXmlAnnotator implements Annotator {


    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode() != null 
                && (
                        XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN.equals(element.getNode().getElementType())
                        || XmlTokenType.XML_DATA_CHARACTERS.equals(element.getNode().getElementType())
                )
        ) {
            String value = element.getText();
            AnnotatorUtil.createNodeTypeAnnotations(element, holder, value);
        }
    }
}
