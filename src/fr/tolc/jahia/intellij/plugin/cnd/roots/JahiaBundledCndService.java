package fr.tolc.jahia.intellij.plugin.cnd.roots;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Owns the copy of the bundled Jahia definitions that lives outside the plugin jar.
 *
 * <p>The 45 base {@code .cnd} files ship inside the plugin jar, but they can only be indexed --
 * and therefore only be resolved, navigated to and completed -- once they exist as real files
 * under a root the platform indexes. They are extracted once per plugin version into
 * {@link PathManager#getSystemPath()}, and exposed by {@link JahiaBundledCndRootsProvider}.
 *
 * <p>{@code getSystemPath()} rather than the plugin installation directory: the installation
 * directory can be read-only, and it is wiped on every plugin update. The extraction folder is
 * keyed by plugin version, so an update lands in a fresh folder and stale ones are purged.
 */
@Service(Service.Level.APP)
public final class JahiaBundledCndService {
    private static final Logger logger = Logger.getInstance(JahiaBundledCndService.class);

    private static final String PLUGIN_ID = "fr.tolc.jahia.intellij.plugin";

    /** Name shown under External Libraries, and used as the library id in root-change events. */
    public static final String LIBRARY_NAME = "Jahia base CND definitions";

    /** Resource folder inside the plugin jar. */
    private static final String BUNDLED_FOLDER = "jahia";
    /** Manifest of the bundled .cnd files, generated at build time by the generateCndIndex task. */
    private static final String INDEX_RESOURCE = BUNDLED_FOLDER + "/index.txt";

    private static final String CACHE_FOLDER = "jahia-cnd";
    private static final String CND_SUBFOLDER = "cnd";
    private static final String LIB_SUBFOLDER = "lib";
    /** Written last: its presence is what marks an extraction as complete. */
    private static final String MARKER = ".complete";

    public static final String COMPLETION_JAR = "jahia-plugin-completion-library.jar";
    public static final String COMPLETION_SOURCES_JAR = "jahia-plugin-completion-library-sources.jar";

    /** Name shown under External Libraries for the JSP completion stubs. */
    public static final String COMPLETION_LIBRARY_NAME = "Jahia completion library";

    private volatile VirtualFile cndRoot;
    private volatile VirtualFile completionClassesRoot;
    private volatile VirtualFile completionSourcesRoot;

    public static JahiaBundledCndService getInstance() {
        return ApplicationManager.getApplication().getService(JahiaBundledCndService.class);
    }

    /**
     * Returns the extracted root, or null while the extraction has not completed.
     *
     * <p>Deliberately non-blocking: no I/O, no VFS refresh, no lock. Its caller
     * {@link JahiaBundledCndRootsProvider} runs on a background thread during index setup, often
     * under a read lock, where touching the file system would be illegal.
     */
    public @Nullable VirtualFile getCndRootIfReady() {
        VirtualFile root = cndRoot;
        return root != null && root.isValid() ? root : null;
    }

    /**
     * Root of the extracted completion jar, or null while the extraction has not completed. Same
     * non-blocking contract as {@link #getCndRootIfReady()}.
     */
    public @Nullable VirtualFile getCompletionClassesRootIfReady() {
        VirtualFile root = completionClassesRoot;
        return root != null && root.isValid() ? root : null;
    }

    /** Sources counterpart of {@link #getCompletionClassesRootIfReady()}. */
    public @Nullable VirtualFile getCompletionSourcesRootIfReady() {
        VirtualFile root = completionSourcesRoot;
        return root != null && root.isValid() ? root : null;
    }

    /**
     * Extracts the bundled definitions, once per plugin version. Idempotent, and safe to call
     * from any thread.
     *
     * <p>Must NOT be called under a read lock, nor on the EDT: it does file I/O and refreshes the
     * VFS synchronously.
     */
    public synchronized void ensureExtracted() throws IOException {
        if (getCndRootIfReady() != null) {
            return;
        }

        Path versionFolder = versionFolder();
        if (!Files.exists(versionFolder.resolve(MARKER))) {
            extractTo(versionFolder);
        }
        purgeOtherVersions(versionFolder);

        VirtualFile extracted = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(versionFolder);
        if (extracted == null) {
            throw new IOException("Extracted folder is not visible in the VFS: " + versionFolder);
        }

        // Synchronous and recursive on purpose. The files were just written through NIO, so the
        // VFS knows nothing of them; publishing a root before its children are visible would hand
        // the indexer an empty directory and silently break nodetype resolution -- the exact
        // failure this whole mechanism exists to prevent. Legal here because the only caller runs
        // on a background thread with no read lock held.
        VfsUtil.markDirtyAndRefresh(false, true, true, extracted);

        VirtualFile root = extracted.findChild(CND_SUBFOLDER);
        if (root == null) {
            throw new IOException("Extracted CND folder is not visible in the VFS: " + versionFolder);
        }

        Path libFolder = versionFolder.resolve(LIB_SUBFOLDER);
        completionClassesRoot = findJarRoot(libFolder.resolve(COMPLETION_JAR));
        completionSourcesRoot = findJarRoot(libFolder.resolve(COMPLETION_SOURCES_JAR));

        cndRoot = root;
        logger.info("Bundled Jahia definitions ready at " + versionFolder);
    }

    /**
     * Resolves the root inside a jar.
     *
     * <p>Built with {@link VirtualFileManager#constructUrl} rather than by concatenating
     * {@code "jar://" + absolutePath + "!/"}, which is what the module-library code did: on Windows
     * that produced {@code jar://C:\Users\...\x.jar!/}, with backslashes a VFS URL never accepts.
     */
    private static @Nullable VirtualFile findJarRoot(@NotNull Path jar) {
        String url = VirtualFileManager.constructUrl(
                JarFileSystem.PROTOCOL,
                FileUtil.toSystemIndependentName(jar.toString()) + JarFileSystem.JAR_SEPARATOR);
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
    }

    private static void extractTo(@NotNull Path versionFolder) throws IOException {
        // A folder without its marker is the leftover of an interrupted extraction: start clean
        // rather than trust a partial copy.
        NioFiles.deleteRecursively(versionFolder);

        Path cndFolder = versionFolder.resolve(CND_SUBFOLDER);
        Files.createDirectories(cndFolder);
        for (String name : readIndex()) {
            copyResource(BUNDLED_FOLDER + "/" + name, cndFolder.resolve(name));
        }

        Path libFolder = versionFolder.resolve(LIB_SUBFOLDER);
        Files.createDirectories(libFolder);
        copyResource(BUNDLED_FOLDER + "/" + COMPLETION_JAR, libFolder.resolve(COMPLETION_JAR));
        copyResource(BUNDLED_FOLDER + "/" + COMPLETION_SOURCES_JAR, libFolder.resolve(COMPLETION_SOURCES_JAR));

        // Last, so an interrupted extraction is never mistaken for a complete one.
        Files.write(versionFolder.resolve(MARKER), new byte[0]);
    }

    private static @NotNull List<String> readIndex() throws IOException {
        try (InputStream in = classLoader().getResourceAsStream(INDEX_RESOURCE)) {
            if (in == null) {
                throw new FileNotFoundException(INDEX_RESOURCE + " is missing from the plugin jar");
            }
            List<String> names = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String name = line.trim();
                if (name.isEmpty()) {
                    continue;
                }
                // The index is generated, but it ends up resolved against a path: refuse anything
                // that could escape the extraction folder.
                if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
                    throw new IOException("Illegal entry in " + INDEX_RESOURCE + ": " + name);
                }
                names.add(name);
            }
            if (names.isEmpty()) {
                throw new IOException(INDEX_RESOURCE + " lists no file");
            }
            return names;
        }
    }

    private static void copyResource(@NotNull String resource, @NotNull Path target) throws IOException {
        try (InputStream in = classLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new FileNotFoundException(resource + " is missing from the plugin jar");
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void purgeOtherVersions(@NotNull Path keep) {
        Path cacheRoot = cacheRoot();
        if (!Files.isDirectory(cacheRoot)) {
            return;
        }
        try (Stream<Path> children = Files.list(cacheRoot)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child) && !child.equals(keep)) {
                    try {
                        NioFiles.deleteRecursively(child);
                    } catch (IOException e) {
                        // A stale folder that cannot be deleted costs disk space and nothing else.
                        logger.warn("Could not delete stale CND cache folder " + child, e);
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Could not list the CND cache folder " + cacheRoot, e);
        }
    }

    private static @NotNull Path versionFolder() {
        return cacheRoot().resolve(pluginVersion());
    }

    private static @NotNull Path cacheRoot() {
        return Path.of(PathManager.getSystemPath(), CACHE_FOLDER);
    }

    private static @NotNull String pluginVersion() {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        String version = descriptor != null ? descriptor.getVersion() : null;
        if (version == null) {
            return "unknown";
        }
        // The version becomes a folder name; snapshot qualifiers may carry anything.
        return version.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static @NotNull ClassLoader classLoader() {
        return JahiaBundledCndService.class.getClassLoader();
    }
}
