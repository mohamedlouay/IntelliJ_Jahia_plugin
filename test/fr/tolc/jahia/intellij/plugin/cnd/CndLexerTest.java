package fr.tolc.jahia.intellij.plugin.cnd;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LexerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Golden token-stream tests over the lexer states that have actually broken before.
 *
 * <p>{@code Cnd.flex} carries more than forty {@code %state} declarations, and every grammar fix in
 * the changelog lands in one of a handful of them. The fixtures here target exactly those:
 *
 * <table>
 *   <tr><th>Fixture</th><th>State</th><th>Regression it covers</th></tr>
 *   <tr><td>propertyConstraint</td><td>PROPERTY_CONSTRAINT</td><td>escaped quotes, issue #64, fixed in 2.2.0</td></tr>
 *   <tr><td>propertyDefaultValue</td><td>PROPERTY_DEFAULT_VALUE</td><td>parentheses inside quotes, issue #62, fixed in 2.1.0; escaped quotes, issue #64, fixed in 2.1.1</td></tr>
 *   <tr><td>options</td><td>OPTIONS</td><td>unquoted + and - in choicelists, issue #47</td></tr>
 *   <tr><td>nodeDefaultValue</td><td>NODE_DEFAULT_VALUE_NAMESPACE</td><td>-</td></tr>
 *   <tr><td>namespaceDeclaration</td><td>initial</td><td>-</td></tr>
 * </table>
 *
 * <p>Deliberately not extending {@link LexerTestCase}: its {@code doFileTest} resolves test data
 * under {@code PathManager.getHomePath()}, the IDE installation, not the project. Only its
 * {@code printTokens} dump format is reused, so the output is the same as any other IntelliJ lexer
 * test.
 *
 * <p>To re-record after an intentional lexer change: delete the {@code .txt} beside the fixture and
 * run once. It writes the dump and fails; read the diff before committing it.
 */
public class CndLexerTest {

    private static final Path LEXER_DIR = Path.of("testResources", "lexer");

    @Test
    public void namespaceDeclaration() {
        doFileTest("namespaceDeclaration");
    }

    @Test
    public void propertyConstraint() {
        doFileTest("propertyConstraint");
    }

    @Test
    public void propertyDefaultValue() {
        doFileTest("propertyDefaultValue");
    }

    @Test
    public void options() {
        doFileTest("options");
    }

    @Test
    public void nodeDefaultValue() {
        doFileTest("nodeDefaultValue");
    }

    /**
     * Syntax the lexer rejects, pinned so that supporting any of it becomes a visible baseline
     * change rather than a silent one.
     *
     * <p>None of these forms occurs anywhere in the sixteen real definition files under
     * {@code testResources/parsing}, so this is a record of where the grammar stops, not a
     * reproduction of a reported bug. The third line is the one exception: it is copied verbatim
     * from {@code V7121StandardTypes.cnd:11}, the single line of real Jahia input the parser
     * cannot handle.
     */
    @Test
    public void unsupportedSyntax() {
        doFileTest("unsupportedSyntax");
    }

    private static void doFileTest(String name) {
        // Line separators are normalised so the golden files do not depend on how git checked the
        // fixtures out -- this repository is worked on from Windows.
        String text = StringUtil.convertLineSeparators(read(LEXER_DIR.resolve(name + ".cnd")));
        String actual = LexerTestCase.printTokens(text, 0, new CndLexerAdapter());

        Path golden = LEXER_DIR.resolve(name + ".txt");
        if (!Files.isRegularFile(golden)) {
            write(golden, actual);
            fail("No lexer baseline for " + name + "; one has just been written to " + golden
                    + ". Read it, then commit it.");
        }

        assertEquals("Token stream changed for " + name,
                StringUtil.convertLineSeparators(read(golden)), actual);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Cannot read " + file.toAbsolutePath() + "; tests must run from the project root", e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
