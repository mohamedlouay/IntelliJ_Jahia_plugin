package fr.tolc.jahia.intellij.plugin.cnd.components;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsListener;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.vfs.VirtualFile;
import fr.tolc.jahia.intellij.plugin.cnd.roots.JahiaBundledCndService;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Triggers the extraction of the bundled Jahia definitions when a project opens.
 *
 * <p>Everything else this class used to do is gone: it generated a jar inside the plugin
 * installation directory, then attached it -- plus the completion jars -- as module libraries from
 * a write action on the EDT, leaking a {@code ModifiableRootModel} on every subsequent project
 * open and mutating the user's module model. The roots now come from
 * {@code JahiaBundledCndRootsProvider}, which owns none of those problems.
 */
public final class CndStartupActivity implements ProjectActivity {
    private static final Logger logger = Logger.getInstance(CndStartupActivity.class);

    /**
     * {@code ProjectActivity.execute} is a Kotlin suspend function; from Java it is implemented as
     * a method taking a {@link Continuation} and returning {@code Unit}. That compiles against the
     * platform's own kotlin-stdlib -- cheaper than adding the Kotlin plugin for a single class in
     * an otherwise all-Java project.
     */
    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        JahiaBundledCndService service = JahiaBundledCndService.getInstance();
        boolean alreadyReady = service.getCndRootIfReady() != null;

        try {
            service.ensureExtracted();
        } catch (IOException e) {
            // Degrade gracefully: without the bundled folder the base Jahia nodetypes are simply
            // unavailable, which is not worth failing project startup over.
            logger.warn("Could not extract the bundled Jahia CND definitions; base nodetypes will be unavailable", e);
            return Unit.INSTANCE;
        }

        if (alreadyReady) {
            // Extraction happened for an earlier project; the provider already answers with the
            // libraries, so this project picked them up on its own.
            return Unit.INSTANCE;
        }

        // The provider was queried before the extraction finished, so it returned nothing. The
        // roots that just appeared have to be announced, or they stay unindexed until the next
        // project open.
        announce(project, JahiaBundledCndService.LIBRARY_NAME, "jahia-bundled-cnd",
                service.getCndRootIfReady());
        announce(project, JahiaBundledCndService.COMPLETION_LIBRARY_NAME, "jahia-completion-library",
                service.getCompletionClassesRootIfReady(), service.getCompletionSourcesRootIfReady());

        return Unit.INSTANCE;
    }

    private static void announce(@NotNull Project project,
                                 @NotNull String libraryName,
                                 @NotNull String debugName,
                                 VirtualFile @NotNull ... roots) {
        List<VirtualFile> newRoots = Arrays.stream(roots).filter(Objects::nonNull).toList();
        if (newRoots.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(
                () -> ApplicationManager.getApplication().runWriteAction(
                        () -> AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                                project, libraryName, Collections.emptyList(), newRoots, debugName)),
                project.getDisposed());
    }
}
