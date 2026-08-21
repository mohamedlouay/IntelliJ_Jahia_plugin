package fr.tolc.jahia.intellij.plugin.cnd.utils;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards the two file-access paths that were Windows-only by accident.
 *
 * <p>This plugin was built and run on Windows for its whole life, and it shows. Two defects
 * survived years of use because nothing ever executed this code on another OS:
 *
 * <ul>
 *   <li>{@code getResources} appended a hard-coded {@code "\\"} to the prefix it stripped from
 *       resource paths, <em>after</em> those paths had been normalised to {@code "/"}. On Linux and
 *       macOS the prefix matched nothing, every key came back null, and the map came back empty --
 *       no error, no log line, just no views and no templates.
 *   <li>The file templates were resolved as paths inside the plugin installation directory, which
 *       does not exist when the plugin is installed as a single jar.
 * </ul>
 *
 * <p>Plain JUnit on purpose: neither path needs a project, so neither needs a platform fixture, and
 * these stay in the millisecond range. What makes them worth having is <em>where</em> they run --
 * the build workflow runs on ubuntu-latest, so from now on every push executes this code on Linux.
 * That is the part that was missing, not the assertions.
 *
 * <p>Same package as the code under test: {@code rootPrefix} and {@code relativeKey} are
 * package-private, split out of the directory walk so they can be checked without a project.
 */
public class CndResourcePathTest {

    // -- resource path mapping -------------------------------------------------------------------

    @Test
    public void keysAreRelativeOnUnixPaths() {
        String root = CndProjectFilesUtil.rootPrefix("/home/dev/module/src/main/resources/jsp");
        assertEquals("/home/dev/module/src/main/resources/jsp/", root);
        assertEquals("html/view.jsp",
                CndProjectFilesUtil.relativeKey("/home/dev/module/src/main/resources/jsp/html/view.jsp", root));
    }

    @Test
    public void keysAreRelativeOnWindowsPaths() {
        String root = CndProjectFilesUtil.rootPrefix("C:\\dev\\module\\src\\main\\resources\\jsp");
        assertEquals("C:/dev/module/src/main/resources/jsp/", root);
        assertEquals("html/view.jsp",
                CndProjectFilesUtil.relativeKey("C:\\dev\\module\\src\\main\\resources\\jsp\\html\\view.jsp", root));
    }

    /** Both sides are normalised, so a mixed pair still lines up. */
    @Test
    public void separatorStyleOfTheInputDoesNotMatter() {
        String fromWindows = CndProjectFilesUtil.rootPrefix("C:\\dev\\jsp");
        assertEquals("html/view.jsp", CndProjectFilesUtil.relativeKey("C:/dev/jsp/html/view.jsp", fromWindows));
    }

    /**
     * The defect itself, pinned. This is what the old code compared against: a prefix ending in a
     * backslash, matched against a path whose separators had already become forward slashes.
     */
    @Test
    public void theOldWindowsOnlyPrefixMatchesNothing() {
        String brokenPrefix = "/home/dev/module/src/main/resources/jsp" + "\\";
        assertNull("A backslash-terminated prefix cannot match a normalised path; that was the bug",
                CndProjectFilesUtil.relativeKey(
                        "/home/dev/module/src/main/resources/jsp/html/view.jsp", brokenPrefix));
    }

    /** A file outside the folder yields no key rather than throwing. */
    @Test
    public void fileOutsideTheFolderHasNoKey() {
        String root = CndProjectFilesUtil.rootPrefix("/home/dev/module/src/main/resources/jsp");
        assertNull(CndProjectFilesUtil.relativeKey("/etc/passwd", root));
    }

    // -- templates read from the classpath -------------------------------------------------------

    @Test
    public void everyTemplateIsReadableFromTheClasspath() throws IOException {
        for (String template : new String[]{
                CndFileTemplateUtil.CND_FILE,
                CndFileTemplateUtil.VIEW_JSP,
                CndFileTemplateUtil.VIEW_PROPERTIES}) {
            byte[] content = CndFileTemplateUtil.read(template);
            assertNotNull(template, content);
            assertTrue(template + " is empty", content.length > 0);
        }
    }

    @Test
    public void aMissingTemplateSaysWhichOne() {
        try {
            CndFileTemplateUtil.read("default/no-such-template.jsp");
            fail("Expected a FileNotFoundException");
        } catch (IOException e) {
            assertTrue("The message must name the resource, got: " + e.getMessage(),
                    e.getMessage().contains("default/no-such-template.jsp"));
        }
    }

    @Test
    public void appendConcatenatesAsWrittenToDisk() {
        byte[] template = "<%-- header --%>\n".getBytes(StandardCharsets.UTF_8);
        assertEquals("<%-- header --%>\nbody",
                new String(CndFileTemplateUtil.append(template, "body"), StandardCharsets.UTF_8));
        assertEquals("<%-- header --%>\n",
                new String(CndFileTemplateUtil.append(template, null), StandardCharsets.UTF_8));
        assertEquals("<%-- header --%>\n",
                new String(CndFileTemplateUtil.append(template, ""), StandardCharsets.UTF_8));
    }
}
