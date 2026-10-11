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
 * integration → GLR parser → TSTree). Since roadmap item 11 the corpus runs
 * with zero exclusions — the error-recovery section passes byte-exact.
 */
class JsCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-javascript/test/corpus");

    @Test
    void everyJsCorpusSectionParsesToTheExpectedTree() throws Exception {
        Language language = Language.fromClasspath("/grammars/javascript/tree-sitter-javascript-blob.bin");
        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                Arrays.asList("destructuring.txt", "expressions.txt", "injectables.txt",
                        "literals.txt", "semicolon_insertion.txt", "statements.txt"));
        assertEquals(116, sections.size(), "vendored JS corpus section count drifted");

        List<String> failures = new ArrayList<>();
        for (CorpusUtil.Section section : sections) {
            String key = section.file() + ": '" + section.title() + "'";
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
        int pass = sections.size() - failures.size();
        System.out.println("JS corpus: " + pass + "/" + sections.size() + " sections pass");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertTrue(failures.isEmpty(), failures.size() + " JS sections fail: " + failures);
        assertEquals(116, pass, "every vendored JS corpus section passes byte-exact");
    }
}
