package io.nop.lint.core.lang;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.testing.TreeEquivalenceOracle;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.incremental.IncrementalStats;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremental-parse integration tests (plan Phase 2): the incremental path
 * (old tree present), the explicit full-parse fallback (no old tree), and
 * the fail-closed failure path. The wiring proof (Minimum Rules #23) runs on
 * real {@link EditCalculator} output — no hand-built edit bypass — and reads
 * the backend's reuse counters to prove the edits were actually consumed by
 * {@code TSParser.parseIncremental} against the old tree.
 */
public class TestIncrementalParseIntegration {

    private static final String OLD_SOURCE =
            "class OrderService {\n"
                    + "    int count = 0;\n"
                    + "    String label = \"orders\";\n"
                    + "}\n";

    private static TreeSitterLanguageAdapter javaBinding() {
        return new TreeSitterLanguageAdapter("java",
                Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void incrementalPathConsumesDiffOutputAndEqualsFullParse() {
        TreeSitterLanguageAdapter binding = javaBinding();
        String newSource = "class OrderService {\n"
                + "    int count = 42;\n"
                + "    String label = \"orders\";\n"
                + "    void touch() {}\n"
                + "}\n";

        var oldTree = binding.parse(bytes(OLD_SOURCE));
        IncrementalStats stats = new IncrementalStats();
        var incremental = binding.parseIncremental(oldTree, bytes(newSource), stats);

        TSTree full = TSParser.parse(binding.treeSitter(), bytes(newSource));
        TreeEquivalenceOracle.assertEquivalent(full, incremental.tree(),
                "incremental product must equal the full reparse");
        assertEquals(newSource.length(), incremental.source().length,
                "the new tree spans the edited source");
        assertTrue(stats.reusedSubtrees() > 0,
                "reuse counters must prove the old tree fed the incremental parse: " + stats);
    }

    @Test
    public void identicalSourceStillRoutesThroughIncrementalEntry() {
        TreeSitterLanguageAdapter binding = javaBinding();
        var oldTree = binding.parse(bytes(OLD_SOURCE));
        IncrementalStats stats = new IncrementalStats();
        var reparsed = binding.parseIncremental(oldTree, bytes(OLD_SOURCE), stats);
        TreeEquivalenceOracle.assertEquivalent(oldTree.tree(), reparsed.tree(), "identical source");
        assertTrue(stats.reusedSubtrees() > 0, "empty diff is still an incremental run: " + stats);
    }

    @Test
    public void nullOldTreeFallsBackToFullParseWithoutReuse() {
        TreeSitterLanguageAdapter binding = javaBinding();
        IncrementalStats stats = new IncrementalStats();
        var fallback = binding.parseIncremental(null, bytes(OLD_SOURCE), stats);

        var direct = binding.parse(bytes(OLD_SOURCE));
        TreeEquivalenceOracle.assertEquivalent(direct.tree(), fallback.tree(),
                "the cold-start branch must produce exactly the full parse");
        assertEquals(0, stats.reusedSubtrees(),
                "the fallback must not report reuse: " + stats);
    }

    @Test
    public void mismatchedLanguageBindingFailsClosed() {
        TreeSitterLanguageAdapter first = javaBinding();
        TreeSitterLanguageAdapter second = javaBinding();
        var oldTree = first.parse(bytes(OLD_SOURCE));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> second.parseIncremental(oldTree, bytes(OLD_SOURCE)));
        assertTrue(ex.getMessage().contains("different language binding"),
                "message must name the mismatch: " + ex.getMessage());
    }

    @Test
    public void nullNewSourceFailsClosed() {
        TreeSitterLanguageAdapter binding = javaBinding();
        var oldTree = binding.parse(bytes(OLD_SOURCE));
        NopLintException ex = assertThrows(NopLintException.class,
                () -> binding.parseIncremental(oldTree, null));
        assertTrue(ex.getMessage().contains("newSource"), ex.getMessage());
    }

    @Test
    public void facadeWrapsTheEditedSource() {
        TreeSitterLanguageAdapter binding = javaBinding();
        var oldTree = binding.parse(bytes(OLD_SOURCE));
        String newSource = OLD_SOURCE.replace("int count = 0;", "int count = 1;");
        var tree = binding.parseIncremental(oldTree, bytes(newSource));
        assertNotNull(tree.root());
        assertEquals(newSource, new String(tree.source(), StandardCharsets.UTF_8));
        assertEquals("program", tree.root().kind());
    }
}
