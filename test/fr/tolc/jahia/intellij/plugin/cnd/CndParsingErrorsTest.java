package fr.tolc.jahia.intellij.plugin.cnd;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards the parsing baselines against new parse errors.
 *
 * <p>{@link CndParsingTest} pins the whole PSI tree, so it fails on any change -- including a
 * legitimate one, where the answer is to re-record. That makes it a poor place to notice that a
 * fixture started failing to parse. This test asks the one question that always has the same
 * answer: does any fixture parse with an error that was not already known?
 *
 * <p>It reads the committed {@code .txt} dumps rather than parsing again, so it needs no platform
 * fixture and stays fast. Re-recording a baseline with a new error in it fails here, loudly, with
 * the error message.
 */
public class CndParsingErrorsTest {

    private static final Path PARSING_DIR = Path.of("testResources", "parsing");

    /**
     * The single parse error in the corpus, deliberately frozen.
     *
     * <p>{@code V7121StandardTypes.cnd:11} reads
     * {@code sharedSmallText skin (choicelist[image]) indexed=no < skins()}. The grammar accepts
     * {@code type name (selector)} and it accepts {@code - name (type, selector[args])}, but not
     * {@code type name (selector[args])} -- the Jahia shorthand with a bracketed selector argument.
     * The same file uses {@code sharedSmallText} 48 times and only this line fails, so it is the
     * bracket that is unsupported, not the type.
     *
     * <p>Frozen rather than fixed on purpose: these tests exist so that grammar changes can be made
     * safely, and they have to land before any grammar change does.
     */
    private static final Map<String, Integer> KNOWN_ERRORS = Map.of("V7121StandardTypes.txt", 1);

    @Test
    public void noUnexpectedParseErrors() {
        Map<String, Integer> actual = new TreeMap<>();
        for (Path baseline : baselines()) {
            long errors = lines(baseline).stream().filter(l -> l.contains("PsiErrorElement")).count();
            if (errors > 0) {
                actual.put(baseline.getFileName().toString(), (int) errors);
            }
        }

        assertEquals(
                "The set of fixtures parsing with errors changed. If a baseline was re-recorded, "
                        + "read its PsiErrorElement lines before accepting them; if the grammar was fixed, "
                        + "shrink KNOWN_ERRORS accordingly.",
                new TreeMap<>(KNOWN_ERRORS), actual);
    }

    /** A baseline missing beside a fixture means the golden test never ran for it. */
    @Test
    public void everyFixtureHasABaseline() {
        for (Path fixture : fixtures()) {
            String name = fixture.getFileName().toString().replaceFirst("\\.cnd$", ".txt");
            assertTrue("No parsing baseline recorded for " + fixture.getFileName(),
                    Files.isRegularFile(PARSING_DIR.resolve(name)));
        }
    }

    @Test
    public void corpusIsNotSilentlyEmpty() {
        assertEquals("Fixture count changed; add or remove the matching test method in CndParsingTest.",
                16, fixtures().size());
    }

    private static List<Path> fixtures() {
        return list(".cnd");
    }

    private static List<Path> baselines() {
        return list(".txt");
    }

    private static List<Path> list(String suffix) {
        try (Stream<Path> files = Files.list(PARSING_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot read " + PARSING_DIR.toAbsolutePath() + "; tests must run from the project root", e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
