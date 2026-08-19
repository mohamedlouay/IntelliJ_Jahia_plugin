package fr.tolc.jahia.intellij.plugin.cnd.utils;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads the file templates shipped in the plugin jar, and writes new project files through the VFS.
 *
 * <p>Replaces the previous route, which resolved templates as paths inside the plugin installation
 * directory -- exploding the plugin jar into a scratch folder when the plugin was installed as a
 * single jar -- and then wrote project files with {@code Files.copy}, behind the IDE's back. Files
 * written that way stay invisible until the next VFS refresh.
 */
public final class CndFileTemplateUtil {
    public static final String CND_FILE = "default/cnd-file.cnd";
    public static final String VIEW_JSP = "default/view.jsp";
    public static final String VIEW_PROPERTIES = "default/view.properties";

    private CndFileTemplateUtil() {
    }

    /** Reads a template out of the plugin jar. */
    public static byte @NotNull [] read(@NotNull String resourcePath) throws IOException {
        try (InputStream in = CndFileTemplateUtil.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException(resourcePath + " is missing from the plugin jar");
            }
            return in.readAllBytes();
        }
    }

    /**
     * Returns the existing child, or creates it with the given content.
     *
     * <p>Must run inside a write action.
     */
    public static @NotNull VirtualFile createIfMissing(@NotNull Object requestor,
                                                       @NotNull VirtualFile directory,
                                                       @NotNull String name,
                                                       byte @NotNull [] content) throws IOException {
        VirtualFile existing = directory.findChild(name);
        if (existing != null) {
            return existing;
        }
        VirtualFile created = directory.createChildData(requestor, name);
        created.setBinaryContent(content);
        return created;
    }

    /** Concatenates a template with the text generated for it, both as written to disk. */
    public static byte @NotNull [] append(byte @NotNull [] template, @Nullable String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return template;
        }
        byte[] extra = suffix.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[template.length + extra.length];
        System.arraycopy(template, 0, result, 0, template.length);
        System.arraycopy(extra, 0, result, template.length, extra.length);
        return result;
    }
}
