package fr.tolc.jahia.intellij.plugin.cnd.lineMarker;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import fr.tolc.jahia.intellij.plugin.cnd.icons.CndIcons;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndNamespaceIdentifier;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndNodeType;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndNodeTypeIdentifier;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndProperty;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndPropertyIdentifier;
import fr.tolc.jahia.intellij.plugin.cnd.psi.CndTypes;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndProjectFilesUtil;
import fr.tolc.jahia.intellij.plugin.cnd.utils.CndTranslationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public class CndCndLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        IElementType type = element.getNode().getElementType();
        //Should only create line marker for leaf elements

        if (type.equals(CndTypes.NAMESPACE_NAME) && element.getParent() instanceof CndNamespaceIdentifier cndNamespaceIdentifier) {
            NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(CndIcons.NAMESPACE)
                    .setTarget(null)
                    .setTooltipText("Namespace " + cndNamespaceIdentifier.getNamespace().getNamespaceName());
            result.add(builder.createLineMarkerInfo(element));

        } else  if (type.equals(CndTypes.NODE_TYPE_NAME) && element.getParent() instanceof CndNodeTypeIdentifier cndNodeTypeIdentifier) {
            CndNodeType cndNodeType = cndNodeTypeIdentifier.getNodeType();
            NavigationGutterIconBuilder<PsiElement> builder;

            //Custom icon
            String jahiaWorkFolderPath = CndProjectFilesUtil.getJahiaWorkFolderPath(element);
            String nodeTypeNamespace = cndNodeType.getNodeTypeNamespace();
            if (!StringUtil.isEmptyOrSpaces(jahiaWorkFolderPath) && nodeTypeNamespace != null) {
                String iconPath = jahiaWorkFolderPath + "/icons/" + CndTranslationUtil.convertNodeTypeIdentifierToPropertyName(nodeTypeNamespace, cndNodeType.getNodeTypeName()) + ".png";
                Icon icon = customIcon(iconPath);
                if (icon != null) {
                    builder = NavigationGutterIconBuilder.create(icon)
                            .setTarget(null)
                            .setTooltipText("Node type " + cndNodeType + " custom icon");
                    result.add(builder.createLineMarkerInfo(element));
                }
            }

            if (cndNodeType.isMixin()) {
                 builder = NavigationGutterIconBuilder.create(CndIcons.MIXIN)
                        .setTarget(null)
                        .setTooltipText("Mixin " + cndNodeType);
            } else {
                builder = NavigationGutterIconBuilder.create(CndIcons.NODE_TYPE)
                        .setTarget(null)
                        .setTooltipText("Node type " + cndNodeType);
            }
            result.add(builder.createLineMarkerInfo(element));

        } else if (type.equals(CndTypes.PROPERTY_NAME) && element.getParent() instanceof CndPropertyIdentifier cndPropertyIdentifier) {
            CndProperty cndProperty = cndPropertyIdentifier.getProperty();
            CndNodeType currentNodeType = cndProperty.getNodeType();
            Set<CndProperty> propertiesWithName = currentNodeType.getPropertiesWithName(cndProperty.getPropertyName());
            for (CndProperty propertyWithName : propertiesWithName) {
                CndNodeType nodeType = propertyWithName.getNodeType();
                if (!nodeType.equals(currentNodeType)) {
                    NavigationGutterIconBuilder<PsiElement> builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.OverridingMethod)
                            .setTarget(propertyWithName)
                            .setTooltipText("Property " + cndProperty.getPropertyName() + " overrides the one from " + nodeType);
                    result.add(builder.createLineMarkerInfo(element));
                    break;
                }
            }
        }
    }

    /** Decoded icon, tied to the file revision it was decoded from. */
    private record CachedIcon(long timeStamp, Icon icon) {
    }

    /**
     * Held on the VirtualFile rather than in a static map, so it dies with the file, and keyed on
     * the timestamp, so editing the icon picks the new one up.
     */
    private static final Key<CachedIcon> CUSTOM_ICON = Key.create("jahia.nodeTypeCustomIcon");

    /**
     * Resolves the custom gutter icon a Jahia module may ship in its {@code icons} folder.
     *
     * <p>Three things were wrong with the previous version, all of them on the highlighting path.
     * {@code new File().exists()} is a disk hit from a thread that must not make one, and trips
     * "Slow operations are prohibited". {@code IconLoader.findIcon(URL)} is deprecated and does not
     * cache {@code file:} URLs, so the image was decoded again on every pass. And the URL was built
     * as {@code "file:/" + path}, which on Windows yields {@code file:/C:\Users\...} -- malformed,
     * so the feature never worked there at all.
     *
     * <p>The VFS lookup is the non-refreshing variant: a synchronous refresh is forbidden under the
     * read action this runs in, and an icon belonging to the project is already known to the VFS.
     */
    @Nullable
    private static Icon customIcon(@NotNull String iconPath) {
        VirtualFile iconFile = LocalFileSystem.getInstance().findFileByPath(iconPath);
        if (iconFile == null || !iconFile.isValid() || iconFile.isDirectory()) {
            return null;
        }

        long timeStamp = iconFile.getTimeStamp();
        CachedIcon cached = iconFile.getUserData(CUSTOM_ICON);
        if (cached != null && cached.timeStamp() == timeStamp) {
            return cached.icon();
        }

        Icon icon;
        try {
            icon = IconUtil.toSize(new ImageIcon(iconFile.contentsToByteArray()), 16, 16);
        } catch (IOException e) {
            // Unreadable or not an image. Cached as a miss too, so a broken file is not retried on
            // every highlighting pass.
            icon = null;
        }
        iconFile.putUserData(CUSTOM_ICON, new CachedIcon(timeStamp, icon));
        return icon;
    }
}
