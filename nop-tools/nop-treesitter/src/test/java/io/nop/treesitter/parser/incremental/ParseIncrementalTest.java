package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.parser.glr.ParserOptions;
import io.nop.treesitter.subtree.Subtree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseIncrementalTest {

    private static final Language JSON =
            Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
    private static final Language JAVA =
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");

    private static TSTree parse(Language language, String source) {
        return TSParser.parse(language, source.getBytes(StandardCharsets.UTF_8));
    }

    private static List<TSInputEdit> edits(TSInputEdit... array) {
        return Arrays.asList(array);
    }

    /**
     * The Phase 2 correctness invariant: the incremental tree's full rendering
     * (named-node corpus form and flat field-aware form) is byte-identical to a
     * fresh full parse of the new source, and the root spans the new source.
     */
    private static IncrementalStats assertIncrementalEqualsFullParse(
            Language language, String oldSource, String newSource, List<TSInputEdit> editList) {
        TSTree oldTree = parse(language, oldSource);
        IncrementalStats stats = new IncrementalStats();
        byte[] newBytes = newSource.getBytes(StandardCharsets.UTF_8);
        TSTree incremental = TSParser.parseIncremental(language, oldTree, editList, newBytes,
                ParserOptions.DEFAULT, stats);
        TSTree full = parse(language, newSource);
        assertEquals(full.toSExpression(), incremental.toSExpression(),
                "incremental corpus form must equal full reparse");
        assertEquals(full.toSexpString(true), incremental.toSexpString(true),
                "incremental flat form must equal full reparse");
        assertEquals(newBytes.length, incremental.rootNode().endByte());
        assertTrue(incremental.rootNode().startByte() == 0);
        return stats;
    }

    private static int oldLeafCount(TSTree tree) {
        int[] stack = new int[64];
        int depth = 0;
        stack[depth++] = tree.root();
        int count = 0;
        while (depth > 0) {
            int id = stack[--depth];
            Subtree node = tree.arena().get(id);
            if (node.childCount() == 0) {
                if (node.symbol() > 0 && tree.arena().sizeOf(id) > 0) {
                    count++;
                }
                continue;
            }
            if (depth + node.childCount() > stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            for (int i = 0; i < node.childCount(); i++) {
                stack[depth++] = node.child(i);
            }
        }
        return count;
    }

    @Test
    void insertIntoJsonArrayReusesPrefixAndSuffix() {
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON,
                "[1, 2, 3]",
                "[1, 2, 3, 4]",
                edits(TSInputEdit.of("[1, 2, 3]".getBytes(StandardCharsets.UTF_8),
                        "[1, 2, 3, 4]".getBytes(StandardCharsets.UTF_8), 8, 8, 11)));
        assertTrue(stats.reusedSubtrees() > 0, "insert must reuse the unedited leaves: " + stats);
    }

    @Test
    void deleteFromJsonArrayReusesAroundTheGap() {
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON,
                "[1, 2, 3]",
                "[1, 3]",
                edits(TSInputEdit.of("[1, 2, 3]".getBytes(StandardCharsets.UTF_8),
                        "[1, 3]".getBytes(StandardCharsets.UTF_8), 4, 7, 4)));
        assertTrue(stats.reusedSubtrees() > 0, "delete must reuse the unedited leaves: " + stats);
    }

    @Test
    void replaceInsideJsonObjectString() {
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON,
                "{\"a\": 1}",
                "{\"a\": 2}",
                edits(TSInputEdit.of("{\"a\": 1}".getBytes(StandardCharsets.UTF_8),
                        "{\"a\": 2}".getBytes(StandardCharsets.UTF_8), 6, 7, 7)));
        assertTrue(stats.reusedSubtrees() > 0, "replace must reuse the unedited leaves: " + stats);
    }

    @Test
    void editInsideJsonStringValue() {
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON,
                "{\"msg\": \"hello world\"}",
                "{\"msg\": \"hello brave world\"}",
                edits(TSInputEdit.of("{\"msg\": \"hello world\"}".getBytes(StandardCharsets.UTF_8),
                        "{\"msg\": \"hello brave world\"}".getBytes(StandardCharsets.UTF_8), 15, 15, 21)));
        assertTrue(stats.reusedSubtrees() > 0, "string edit must reuse the unedited leaves: " + stats);
    }

    @Test
    void multiEditAppliesBothEdits() {
        String oldSource = "{\"a\": 1, \"b\": 2}";
        String newSource = "{\"a\": 10, \"b\": 22}";
        byte[] oldBytes = oldSource.getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = newSource.getBytes(StandardCharsets.UTF_8);
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON, oldSource, newSource,
                edits(TSInputEdit.of(oldBytes, newBytes, 6, 7, 8),
                        TSInputEdit.of(oldBytes, newBytes, 12, 13, 14)));
        assertTrue(stats.reusedSubtrees() > 0, "multi-edit must reuse the unedited leaves: " + stats);
    }

    @Test
    void nestedEditDeepInsideTree() {
        IncrementalStats stats = assertIncrementalEqualsFullParse(JSON,
                "[[1, 2], [3, 4]]",
                "[[1, 2], [3, 44]]",
                edits(TSInputEdit.of("[[1, 2], [3, 4]]".getBytes(StandardCharsets.UTF_8),
                        "[[1, 2], [3, 44]]".getBytes(StandardCharsets.UTF_8), 14, 15, 16)));
        assertTrue(stats.reusedSubtrees() > 0, "nested edit must reuse the unedited leaves: " + stats);
    }

    @Test
    void noOpEditReusesEveryOldLeafAndLexesOnlyEndToken() {
        TSTree oldTree = parse(JSON, "[1,2,3]");
        int leaves = oldLeafCount(oldTree);
        assertEquals(7, leaves, "fixture must have exactly the seven visible leaves");
        IncrementalStats stats = new IncrementalStats();
        byte[] sameBytes = "[1,2,3]".getBytes(StandardCharsets.UTF_8);
        TSTree incremental = TSParser.parseIncremental(JSON, oldTree,
                edits(TSInputEdit.of(sameBytes, sameBytes, 3, 3, 3)), sameBytes,
                ParserOptions.DEFAULT, stats);
        assertEquals(TSParser.parse(JSON, sameBytes).toSExpression(), incremental.toSExpression());
        assertEquals(leaves, stats.reusedSubtrees(), "no-op edit must reuse every old leaf: " + stats);
        assertEquals(1, stats.lexedTokens(), "no-op edit must lex only the end token: " + stats);
    }

    @Test
    void whitespaceGapsBlockLeafReuseLikeUpstream() {
        TSTree oldTree = parse(JSON, "[1, 2, 3]");
        IncrementalStats stats = new IncrementalStats();
        byte[] sameBytes = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        TSTree incremental = TSParser.parseIncremental(JSON, oldTree,
                edits(TSInputEdit.of(sameBytes, sameBytes, 5, 5, 5)), sameBytes,
                ParserOptions.DEFAULT, stats);
        assertEquals(TSParser.parse(JSON, sameBytes).toSExpression(), incremental.toSExpression());
        assertEquals(5, stats.reusedSubtrees(),
                "leaves aligned with the scan position reuse; leaves behind whitespace gaps re-lex (C-identical): "
                        + stats);
    }

    @Test
    void sameOldTreeSupportsTwoIndependentIncrementalParses() {
        TSTree oldTree = parse(JSON, "[1, 2, 3]");
        byte[] a = "[1, 2, 3, 4]".getBytes(StandardCharsets.UTF_8);
        byte[] b = "[0, 1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        TSTree first = TSParser.parseIncremental(JSON, oldTree,
                edits(TSInputEdit.of("[1, 2, 3]".getBytes(StandardCharsets.UTF_8), a, 8, 8, 11)), a);
        TSTree second = TSParser.parseIncremental(JSON, oldTree,
                edits(TSInputEdit.of("[1, 2, 3]".getBytes(StandardCharsets.UTF_8), b, 1, 1, 4)), b);
        assertEquals(TSParser.parse(JSON, a).toSExpression(), first.toSExpression());
        assertEquals(TSParser.parse(JSON, b).toSExpression(), second.toSExpression());
    }

    @Test
    void javaMethodBodyEditReusesSurroundingClass() {
        String oldSource = "class A {\n    void m() {\n        int x = 1;\n    }\n}\n";
        String newSource = "class A {\n    void m() {\n        int x = 2;\n        int y = 3;\n    }\n}\n";
        byte[] oldBytes = oldSource.getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = newSource.getBytes(StandardCharsets.UTF_8);
        IncrementalStats stats = assertIncrementalEqualsFullParse(JAVA, oldSource, newSource,
                edits(TSInputEdit.of(oldBytes, newBytes, 38, 39, 39)));
        assertTrue(stats.reusedSubtrees() > 0, "java edit must reuse the unedited leaves: " + stats);
    }

    @Test
    void javaFieldInsertInsideClass() {
        String oldSource = "class A {\n    int a = 1;\n}\n";
        String newSource = "class A {\n    int a = 1;\n    int b = 2;\n}\n";
        byte[] oldBytes = oldSource.getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = newSource.getBytes(StandardCharsets.UTF_8);
        IncrementalStats stats = assertIncrementalEqualsFullParse(JAVA, oldSource, newSource,
                edits(TSInputEdit.of(oldBytes, newBytes, 25, 25, 40)));
        assertTrue(stats.reusedSubtrees() > 0, "java insert must reuse the unedited leaves: " + stats);
    }

    @Test
    void overlappingEditsFailLoudly() {
        TSTree oldTree = parse(JSON, "[1, 2, 3]");
        byte[] bytes = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        List<TSInputEdit> overlapping = edits(
                TSInputEdit.of(bytes, bytes, 1, 4, 4),
                TSInputEdit.of(bytes, bytes, 2, 6, 6));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, oldTree, overlapping, bytes));
    }

    @Test
    void outOfRangeEditFailsLoudly() {
        TSTree oldTree = parse(JSON, "[1, 2, 3]");
        byte[] bytes = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, oldTree,
                        edits(TSInputEdit.of(bytes, bytes, 0, 99, 99)), bytes));
    }

    @Test
    void nullAndMismatchedInputsFailLoudly() {
        TSTree oldTree = parse(JSON, "[1]");
        byte[] bytes = "[1]".getBytes(StandardCharsets.UTF_8);
        Language other = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(null, oldTree, Collections.emptyList(), bytes));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, null, Collections.emptyList(), bytes));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, oldTree, null, bytes));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, oldTree, Collections.emptyList(), null));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(other, oldTree, Collections.emptyList(), bytes));
        assertThrows(TreeSitterException.class,
                () -> TSParser.parseIncremental(JSON, oldTree,
                        Collections.singletonList(null), bytes));
    }

    /**
     * Item 11 interplay: the old tree carries ERROR / MISSING nodes, the edit
     * repairs the source, and the item 9 invariants hold — the incremental
     * tree is byte-identical to a fresh parse and the changed ranges cover the
     * repaired span. The reuse cursor's symbol-window filter excludes the
     * error leaves from reuse (pinned here by the mid-token-garbage case).
     */
    @Test
    void incrementalParseOverErrorTreesStaysByteEquivalentToFullReparse() {
        assertIncrementalEqualsFullParse(JSON, "[1, 2", "[1, 2]",
                edits(TSInputEdit.of("[1, 2".getBytes(StandardCharsets.UTF_8),
                        "[1, 2]".getBytes(StandardCharsets.UTF_8), 5, 5, 6)));
        assertIncrementalEqualsFullParse(JSON, "[truex]", "[true]",
                edits(TSInputEdit.of("[truex]".getBytes(StandardCharsets.UTF_8),
                        "[true]".getBytes(StandardCharsets.UTF_8), 5, 6, 5)));

        TSTree broken = parse(JSON, "[1, 2");
        TSTree fixed = TSParser.parseIncremental(JSON, broken,
                edits(TSInputEdit.of("[1, 2".getBytes(StandardCharsets.UTF_8),
                        "[1, 2]".getBytes(StandardCharsets.UTF_8), 5, 5, 6)),
                "[1, 2]".getBytes(StandardCharsets.UTF_8));
        List<TSRange> ranges = TSParser.getChangedRanges(broken, fixed);
        assertEquals(1, ranges.size(), "the repaired tail is one changed range: " + ranges);
        assertEquals(5, ranges.get(0).startByte());
        assertEquals(6, ranges.get(0).endByte());
    }
}
