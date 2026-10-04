package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterBash;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.BashScanner;
import io.nop.treesitter.scanner.ExternalScanner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The full upstream bash corpus (tree-sitter-bash {@code v0.25.1}, 100 sections
 * in 5 files) run through the pure-Java runtime: blob built by {@code Ts2Java}
 * from the vendored {@code src/parser.c}, with all 29 external tokens carried
 * by the hand-translated {@link BashScanner}.
 *
 * <p>Loaded through {@link TreeSitterBash} (wiring evidence).</p>
 */
class BashCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-bash/test/corpus");

    private static final List<String> CORPUS_FILES = List.of(
            "commands.txt", "crlf.txt", "literals.txt", "programs.txt", "statements.txt");

    /**
     * Sections whose trees diverge from the upstream expected trees, each with a
     * root cause. Empty means every section matches upstream exactly.
     */
    private static final List<String> ADJUDICATED = List.of(
    );

    @Test
    void everyBashCorpusSectionParsesToTheExpectedTree() throws Exception {
        TreeSitterBash wrapper = new TreeSitterBash();
        Language language = wrapper.language();

        assertEquals(29, language.externalTokenCount());
        ExternalScanner scanner = language.newExternalScanner();
        assertNotNull(scanner);
        assertTrue(scanner instanceof BashScanner);

        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR, CORPUS_FILES);
        assertEquals(100, sections.size(), "vendored bash corpus section count");

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
                    failures.add(key + " [tree mismatch]\n    expected: "
                            + section.expected().replace("\n", "\n    ")
                            + "\n    actual:   " + actual.replace("\n", "\n    "));
                }
            } catch (Exception e) {
                failures.add(key + " [" + e + "]");
            }
        }
        int pass = sections.size() - failures.size() - adjudicated.size();
        System.out.println("bash corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "bash corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertEquals(sections.size() - adjudicated.size(), pass,
                failures.size() + " unadjudicated bash sections fail");
    }
}
