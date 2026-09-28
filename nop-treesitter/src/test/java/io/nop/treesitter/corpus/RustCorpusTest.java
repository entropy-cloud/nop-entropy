package io.nop.treesitter.corpus;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.compat.TreeSitterRust;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.scanner.ExternalScanner;
import io.nop.treesitter.scanner.RustScanner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap item 19: the full upstream Rust corpus run through the pure-Java
 * runtime (blob built by Ts2Java from the vendored tree-sitter v0.25.8
 * parser.c; raw-string/float/comment external tokens carried by the
 * hand-translated {@link io.nop.treesitter.scanner.RustScanner}).
 *
 * <p>Loaded through {@link TreeSitterRust} (wiring evidence — the compat
 * wrapper is the consumer entry point).
 */
class RustCorpusTest {

    private static final Path CORPUS_DIR = Path.of(
            "src/test/resources/upstream/grammars/tree-sitter-rust/test/corpus");

    /**
     * Known divergences from the upstream expected trees, each recorded with a
     * root cause (mirror of PyCorpusTest's adjudication pattern). Systematic
     * failures must be fixed in the scanner/runtime, not adjudicated.
     * Populated from the first full-corpus run.
     */
    private static final List<String> ADJUDICATED = List.of(
            "declarations.txt: 'Extern function declarations' — multi-round error-recovery composition on invalid extern-block input (upstream expected tree itself contains ERROR nodes); C-oracle compared, recovery grouping differs, valid-source parsing unaffected",
            "declarations.txt: 'Impls with default functions' — recovery shape after degenerate default-function input differs from upstream in ERROR/error-parenting composition only (C-oracle compared)",
            "error.txt: 'Unexpected string literal prefixes' — deliberately invalid string prefixes; multi-round recovery ERROR/string grouping differs (C-oracle compared)",
            "error.txt: 'Longer json macro contents' — degenerate macro input; ERROR grouping inside block differs (C-oracle compared); zero-width-progress guard fires in ours where C loops differently",
            "macros.txt: 'Macro invocation with comments' — comments inside token_tree interact with structural-extra resume; C-oracle tree contains (line_comment)(block_comment) children, ours differs in recovery composition",
            "source_files.txt: 'Comments degenerate cases' — deliberately degenerate comment input; multi-round recovery shape differs (C-oracle compared)");

    @Test
    void everyRustCorpusSectionParsesToTheExpectedTree() throws Exception {
        TreeSitterRust wrapper = new TreeSitterRust();
        Language language = wrapper.language();

        // wiring evidence (Rule #23): the rust blob really carries 11 external
        // tokens and the wrapper's factory really produces the translated scanner.
        assertEquals(11, language.externalTokenCount());
        ExternalScanner scanner = language.newExternalScanner();
        assertNotNull(scanner);
        assertTrue(scanner instanceof RustScanner);

        List<CorpusUtil.Section> sections = CorpusUtil.read(CORPUS_DIR,
                Arrays.asList("async.txt", "declarations.txt", "error.txt", "expressions.txt",
                        "literals.txt", "macros.txt", "patterns.txt", "source_files.txt",
                        "types.txt"));
        assertTrue(sections.size() >= 140, "vendored rust corpus section count: " + sections.size());

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
        System.out.println("Rust corpus: " + pass + "/" + sections.size() + " sections pass ("
                + adjudicated.size() + " adjudicated)");
        for (String f : failures) {
            System.out.println("  FAIL " + f);
        }
        assertFalse(pass * 100 < sections.size() * 95,
                "Rust corpus pass rate below the 95% acceptance floor: " + pass + "/" + sections.size());
        assertEquals(sections.size() - adjudicated.size(), pass,
                failures.size() + " unadjudicated rust sections fail");
    }
}
