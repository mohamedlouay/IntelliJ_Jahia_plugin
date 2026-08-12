package fr.tolc.jahia.intellij.plugin.cnd.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CndPluginUtil {
    private static final String PLUGIN_ID = "fr.tolc.jahia.intellij.plugin";
    private static final String CLASSES_FOLDER = "classes";
    private static final String PLUGIN_FOLDER_NAME = ".IntelliJ_Jahia_Plugin";

    /**
     * Resolved lazily, never in a static initializer: PluginId lookup can return null, and an
     * NPE at class-initialization time would raise ExceptionInInitializerError and take down
     * every caller of this class at once rather than failing the one operation that needs it.
     */
    private static volatile File pluginFolder;

    @Nullable
    private static File getPluginFolder() {
        File folder = pluginFolder;
        if (folder == null) {
            IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
            if (descriptor == null) {
                return null;
            }
            Path path = descriptor.getPluginPath();
            if (path == null) {
                return null;
            }
            folder = path.toFile();
            pluginFolder = folder;
        }
        return folder;
    }

    @Nullable
    public static File getPluginFile(String filePath) {
        File folder = getPluginFolder();
        if (folder == null) {
            return null;
        }

        if (folder.getAbsolutePath().endsWith(".jar")) {
            File plugins = folder.getParentFile();
            File newPluginFolder = new File(plugins.getAbsolutePath() + "/" + PLUGIN_FOLDER_NAME);
            if (newPluginFolder.exists()) {
                try {
                    deleteRecursively(newPluginFolder);
                } catch (IOException e) {
                    //Nothing to do
                }
            }
            extractJarToFolder(folder, newPluginFolder, "plugin.xml");
            // cache the exploded folder, otherwise the jar is re-extracted on every call
            folder = newPluginFolder;
            pluginFolder = newPluginFolder;
        }

        File candidateFile = new File(folder.getAbsolutePath() + "/" + filePath);
        if (candidateFile.exists()) {
            return candidateFile;
        }

        //in case of plugins-sandbox
        return new File(folder.getAbsolutePath() + "/" + CLASSES_FOLDER + "/" + filePath);
    }

    @Nullable
    public static Path getPluginFilePath(String filePath) {
        File pluginFile = getPluginFile(filePath);
        if (pluginFile != null && pluginFile.exists()) {
            return Paths.get(pluginFile.getAbsolutePath());
        }
        return null;
    }

    public static void fileToJar(File rootFile, String jarPath, String... extensions) throws IOException {
        FileOutputStream fout = new FileOutputStream(jarPath);
        JarOutputStream jarOut = new JarOutputStream(fout);
        addFileToJarRecursive(jarOut, rootFile, rootFile, extensions);
        jarOut.close();
        fout.close();
    }

    private static void addFileToJarRecursive(JarOutputStream jarOut, File file, File rootFile, String... extensions) throws IOException {
        if (file.isDirectory()) {
            if (!FileUtil.filesEqual(file, rootFile)) {
                jarOut.putNextEntry(new ZipEntry(getRelativePath(rootFile, file) + "/"));
            }
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addFileToJarRecursive(jarOut, child, rootFile, extensions);
                }
            }
        } else {
            String entryName;
            if (FileUtil.filesEqual(file, rootFile)) {
                entryName = file.getName();
            } else {
                entryName = getRelativePath(rootFile, file);
            }

            String[] split = entryName.split("\\.");
            if (ArrayUtil.contains(split[split.length - 1], extensions)) {
                jarOut.putNextEntry(new ZipEntry(entryName));
                jarOut.write(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
                jarOut.closeEntry();
            }
        }
    }

    private static String getRelativePath(File parent, File child) {
        return child.getAbsolutePath().substring(parent.getAbsolutePath().length() + 1);
    }
    
    public static void extractJarToFolder(File jar, File destFolder, String... ignoreFiles) {
        JarFile jarFile = null;
        FileOutputStream fos = null;
        InputStream is = null;
        
        try {
            jarFile = new JarFile(jar);

            Enumeration<JarEntry> enumEntries = jarFile.entries();
            while (enumEntries.hasMoreElements()) {
                JarEntry file = enumEntries.nextElement();
                
                boolean skip = false;
                for (String ignoreFile : ignoreFiles) {
                    if (file.getName().endsWith(ignoreFile)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }
                
                File f = new File(destFolder.getAbsolutePath() + File.separator + file.getName());
                f.getParentFile().mkdirs();
                
                if (file.isDirectory()) {
                    continue;
                }
                
                is = jarFile.getInputStream(file);  // get the input stream
                fos = new FileOutputStream(f);
                while (is.available() > 0) {  // write contents of 'is' to 'fos'
                    fos.write(is.read());
                }
                fos.close();
                is.close();
            }
            
        } catch (Exception e) {
            //Nothing to do
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
                if (fos != null) {
                    fos.close();
                }
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                //Nothing to do
            }
        }
    }
    
    public static void deleteRecursively(File toDelete) throws IOException {
        Files.walkFileTree(Paths.get(toDelete.getAbsolutePath()), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        toDelete.delete();
    }

    @NotNull
    public static Module[] getProjectModules(Project project) {
        return ModuleManager.getInstance(project).getModules();
    }
}
