package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 10: the full upstream JavaScript corpus (116 sections across
 * six corpus files) run through the same {@code TSParser.parse} path as the
 * unit tests (Language → table-driven Lexer with the external-scan VM
 * integration → GLR parser → TSTree). The only exclusion is the adjudicated
 * error-recovery section (roadmap item 11); unlisted failures fail the suite.
 */
class JsCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/test/corpus");

    /**
     * Adjudicated in-scope deviation (recorded with upstream evidence, not a
     * silent skip): the expected tree contains {@code (ERROR ...)} and
     * {@code (MISSING ";")} nodes, which only the error-recovery machinery can
     * produce — roadmap item 11's scope.
     */
    private static final List<String> ADJUDICATED = List.of(
            "destructuring.txt: 'Extra complex literals in expressions'");

    @Test
    void everyJsCorpusSectionParsesToTheExpectedTree() throws Exception {
        Language language = Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");
        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                Arrays.asList("destructuring.txt", "expressions.txt", "injectables.txt",
                        "literals.txt", "semicolon_insertion.txt", "statements.txt"));
        assertEquals(116, sections.size(), "vendored JS corpus section count drifted");

        List<String> failures = new ArrayList<>();
        List<String> adjudicated = new ArrayList<>();
        for (CorpusUtil.Section section : sections) {
            String key = section.file() + ": '" + section.title() + "'";
            if (ADJUDICATED.contains(key)) {
                adjudicated.add(key);
                continue;
            }
            try {
                TSTree tree = TSParser.parse(language, section.input().getBytes(StandardCharsets.UTF_8));
                String actual = tree.toSexpString(section.hasFields());
                if (!section.expected().equals(actual)) {
                    failures.add(key + " [tree mismatch]");
                }
            } catch (Exception e) {
                failures.add(key + " [" + e + "]");
            }
        }
        int pass = sections.size() - failures.size() - adjudicated.size();
        System.out.println("JS corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "JS corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertTrue(failures.isEmpty(), failures.size() + " unadjudicated JS sections fail: " + failures);
        assertEquals(1, adjudicated.size(), "the single adjudicated error-recovery section");
    }
}
