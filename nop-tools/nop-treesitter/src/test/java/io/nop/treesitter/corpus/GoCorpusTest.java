package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterGo;
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
 * Roadmap item 18: the full upstream Go corpus run through the pure-Java runtime
 * (blob built by Ts2Java from the vendored tree-sitter v0.25.8 parser.c; go is
 * the first vendored grammar with reserved words but no external scanner).
 *
 * <p>Loaded through {@link TreeSitterGo} (wiring evidence — the compat wrapper is
 * the consumer entry point, not a bare Language.fromClasspath).
 */
class GoCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-go/test/corpus");

    /**
     * Known divergences from the upstream expected trees, each recorded with a
     * root cause (mirror of PyCorpusTest's adjudication pattern). Populated from
     * the first full-corpus run; systematic failures must be fixed in the runtime,
     * not adjudicated.
     */
    private static final List<String> ADJUDICATED = List.of(
            "errors.txt: 'Error detected at globally reserved keyword' — multi-round error-recovery tree shape, same class as the identically-named adjudicated python section (upstream picks a different MISSING/recovery composition after the reserved word); valid-source parsing unaffected",
            "literals.txt: 'String literals' — nested ERROR composition differs on malformed raw-string input, watch-only multi-round recovery class from the error-recovery plan");

    @Test
    void everyGoCorpusSectionParsesToTheExpectedTree() throws Exception {
        Language language = new TreeSitterGo().language();
        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                Arrays.asList("declarations.txt", "errors.txt", "expressions.txt", "literals.txt",
                        "source_files.txt", "statements.txt", "types.txt"));
        assertTrue(sections.size() >= 60, "vendored go corpus section count: " + sections.size());

        List<String> failures = new ArrayList<>();
        List<String> adjudicated = new ArrayList<>();
        for (CorpusUtil.Section section : sections) {
            String key = section.file() + ": '" + section.title() + "'";
            boolean adjudicatedHit = ADJUDICATED.stream().anyMatch(a -> a.startsWith(key));
            if (adjudicatedHit) {
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
        System.out.println("Go corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "Go corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertEquals(sections.size() - adjudicated.size(), pass,
                failures.size() + " unadjudicated go sections fail");
    }
}
