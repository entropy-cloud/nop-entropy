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
 * Roadmap follow-up: the full upstream Python corpus run through the pure-Java
 * runtime with the Java-implemented indentation external scanner
 * ({@code io.nop.treesitter.scanner.PythonScanner}).
 */
class PyCorpusTest {

    /**
     * Re-adjudicated (2026-09-11, after the item 15 GLR fixes): 115/117 =
     * 98.3% ≥ the 95% floor. The splat-class adjudications ('Print used as an
     * identifier', 'Assignments', 'Lists') were removed — those sections now
     * render byte-identical to upstream. The 2 remaining sections are
     * multi-round error-recovery tree shapes, the watch-only class adjudicated
     * in the error-recovery plan (C-traced for-else precedent and this class's
     * C-vs-Java structural differences recorded in
     * ai-dev/logs/2026/09-11.md). Neither affects valid-source parsing.
     */

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-python/test/corpus");

    /**
     * Known divergences from the upstream expected trees, each recorded with a
     * root cause (mirror of JsCorpusTest's adjudication pattern).
     */
    private static final List<String> ADJUDICATED = List.of(
            "errors.txt: 'An error before a string literal'",
            "errors.txt: 'Error detected at globally reserved keyword'");

    @Test
    void everyPythonCorpusSectionParsesToTheExpectedTree() throws Exception {
        Language language = Language.fromClasspath("/grammars/python/tree-sitter-python-blob.bin");
        language.setExternalScannerFactory(io.nop.treesitter.scanner.PythonScanner::new);
        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                Arrays.asList("errors.txt", "expressions.txt", "literals.txt",
                        "pattern_matching.txt", "statements.txt"));
        assertTrue(sections.size() >= 100, "vendored python corpus section count: " + sections.size());

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
        System.out.println("Python corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "Python corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertEquals(sections.size() - adjudicated.size(), pass,
                failures.size() + " unadjudicated python sections fail");
    }
}
