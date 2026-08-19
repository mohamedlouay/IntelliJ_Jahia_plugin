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

/**
 * The bundled Jahia definitions, seen as one library.
 *
 * <p>Declared through source roots rather than binary roots: these are {@code .cnd} files parsed
 * by this plugin, not compiled artifacts. What matters is that the root is indexed and therefore
 * part of {@code GlobalSearchScope.allScope}, which is what every CND lookup goes through.
 *
 * <p>Implements {@link ItemPresentation} so the entry carries a name and an icon under External
 * Libraries instead of showing up as a bare path.
 */
final class JahiaBundledCndLibrary extends SyntheticLibrary implements ItemPresentation {
    private final VirtualFile root;

    JahiaBundledCndLibrary(@NotNull VirtualFile root) {
        this.root = root;
    }

    @Override
    public @NotNull Collection<VirtualFile> getSourceRoots() {
        return Collections.singletonList(root);
    }

    // SyntheticLibrary declares equals/hashCode abstract: the platform compares instances to
    // decide whether roots actually changed, and a provider builds a new instance on every call.
    @Override
    public boolean equals(Object o) {
        return o instanceof JahiaBundledCndLibrary && root.equals(((JahiaBundledCndLibrary) o).root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }

    @Override
    public @Nullable String getPresentableText() {
        return JahiaBundledCndService.LIBRARY_NAME;
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
