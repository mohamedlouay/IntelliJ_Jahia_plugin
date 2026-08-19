package fr.tolc.jahia.intellij.plugin.cnd.roots;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import fr.tolc.jahia.intellij.plugin.cnd.icons.CndIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * The JSP completion stubs, seen as one library.
 *
 * <p>Kept separate from {@link JahiaBundledCndLibrary} because the two have different demands.
 * The .cnd files only need to be in the search scope; this jar has to be resolved by the Java
 * subsystem, so that {@code JavaPsiFacade} turns
 * {@code org.jahia.services.content.mod.JCRNodeWrapperMod} into a PsiClass and
 * {@code CndJspElVariablesProvider} can type the implicit EL variable {@code currentNode}. Hence
 * binary roots here, where the definitions use source roots.
 */
final class JahiaCompletionLibrary extends SyntheticLibrary implements ItemPresentation {
    private final VirtualFile classesRoot;
    private final VirtualFile sourcesRoot;

    JahiaCompletionLibrary(@NotNull VirtualFile classesRoot, @Nullable VirtualFile sourcesRoot) {
        this.classesRoot = classesRoot;
        this.sourcesRoot = sourcesRoot;
    }

    @Override
    public @NotNull Collection<VirtualFile> getBinaryRoots() {
        return Collections.singletonList(classesRoot);
    }

    @Override
    public @NotNull Collection<VirtualFile> getSourceRoots() {
        // The sources jar is a convenience for go-to-declaration; the library works without it.
        return sourcesRoot == null ? Collections.emptyList() : Collections.singletonList(sourcesRoot);
    }

    // SyntheticLibrary declares equals/hashCode abstract: the platform compares instances to
    // decide whether roots actually changed, and a provider builds a new instance on every call.
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JahiaCompletionLibrary)) {
            return false;
        }
        JahiaCompletionLibrary other = (JahiaCompletionLibrary) o;
        return classesRoot.equals(other.classesRoot) && Objects.equals(sourcesRoot, other.sourcesRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classesRoot, sourcesRoot);
    }

    @Override
    public @Nullable String getPresentableText() {
        return JahiaBundledCndService.COMPLETION_LIBRARY_NAME;
    }

    @Override
    public @Nullable String getLocationString() {
        return null;
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        return CndIcons.FILE;
    }
}
