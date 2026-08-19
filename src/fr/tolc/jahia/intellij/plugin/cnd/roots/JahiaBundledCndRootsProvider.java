package fr.tolc.jahia.intellij.plugin.cnd.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Puts the bundled Jahia definitions in the project's library scope.
 *
 * <p>This replaces the module libraries the plugin used to create by hand at project open. The
 * consumers are untouched: the roots returned here are indexed and included in
 * {@code GlobalSearchScope.allScope}, which is what {@code CndProjectFilesUtil.getProjectCndFiles}
 * already queried. Nothing mutates the user's module model any more.
 */
public final class JahiaBundledCndRootsProvider extends AdditionalLibraryRootsProvider {

    /**
     * Called on a background thread while indexes are being set up, frequently under a read lock.
     * It must stay allocation-cheap and do no I/O -- hence the non-blocking
     * {@link JahiaBundledCndService#getCndRootIfReady()}, which returns null until the extraction
     * has finished. {@code CndStartupActivity} announces the roots once they appear.
     */
    @Override
    public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        JahiaBundledCndService service = JahiaBundledCndService.getInstance();

        List<SyntheticLibrary> libraries = new ArrayList<>(2);

        VirtualFile cndRoot = service.getCndRootIfReady();
        if (cndRoot != null) {
            libraries.add(new JahiaBundledCndLibrary(cndRoot));
        }

        VirtualFile completionClasses = service.getCompletionClassesRootIfReady();
        if (completionClasses != null) {
            libraries.add(new JahiaCompletionLibrary(completionClasses, service.getCompletionSourcesRootIfReady()));
        }

        return libraries;
    }

    // getRootsToWatch is left at its empty default: the extracted content is immutable, written
    // once per plugin version, so there is nothing worth a file watcher.
}
