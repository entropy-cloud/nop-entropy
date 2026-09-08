package io.nop.treesitter.parser.incremental;

import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.TreeSitterException;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.Subtree;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangedRangesTest {

    private static final Language JSON =
            Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
    private static final Language JAVA =
            Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin");

    private static TSTree parse(Language language, String source) {
        return TSParser.parse(language, source.getBytes(StandardCharsets.UTF_8));
    }

    private static TSTree incremental(Language language, String oldSource, String newSource, TSInputEdit edit) {
        TSTree oldTree = parse(language, oldSource);
        return TSParser.parseIncremental(language, oldTree, Arrays.asList(edit),
                newSource.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Independent mechanical cross-check (separate code path from the recursive
     * structural diff): flatten both trees to leaf spines and compare them with
     * a content-aware two-pointer walk. Every byte covered by a differing or
     * displaced leaf pair must lie inside some reported range; conversely every
     * byte outside the reported ranges carries identical tree content (same
     * symbol, same content bytes) in both trees.
     */
    private static void assertRangesMatchLeafDiffs(TSTree oldTree, TSTree newTree, List<TSRange> ranges) {
        List<int[]> oldSpine = leafSpine(oldTree);
        List<int[]> newSpine = leafSpine(newTree);
        byte[] oldSource = oldTree.source();
        byte[] newSource = newTree.source();
        int i = 0;
        int j = 0;
        while (i < oldSpine.size() || j < newSpine.size()) {
            if (i < oldSpine.size() && j < newSpine.size()) {
                int[] o = oldSpine.get(i);
                int[] n = newSpine.get(j);
                if (o[2] == n[2] && o[1] - o[0] == n[1] - n[0]
                        && contentEquals(oldSource, o[0], newSource, n[0], o[1] - o[0])) {
                    i++;
                    j++;
                    continue;
                }
                int start = Math.min(o[0], n[0]);
                int end = Math.max(o[1], n[1]);
                assertTrue(covered(ranges, start, end),
                        () -> "diff at old[" + o[0] + "," + o[1] + ") new[" + n[0] + "," + n[1]
                                + ") not covered by " + ranges);
                int limit = Math.min(o[1], n[1]);
                i = nextIndex(oldSpine, limit, i);
                j = nextIndex(newSpine, limit, j);
                continue;
            }
            int[] rest = i < oldSpine.size() ? oldSpine.get(i) : newSpine.get(j);
            assertTrue(covered(ranges, rest[0], rest[1]),
                    () -> "trailing diff [" + rest[0] + "," + rest[1] + ") not covered by " + ranges);
            if (i < oldSpine.size()) {
                i++;
            } else {
                j++;
            }
        }
    }

    private static int nextIndex(List<int[]> spine, int position, int currentIndex) {
        while (currentIndex < spine.size() && spine.get(currentIndex)[1] <= position) {
            currentIndex++;
        }
        return currentIndex;
    }

    private static List<int[]> leafSpine(TSTree tree) {
        List<int[]> out = new ArrayList<>();
        int[] stack = new int[64];
        int depth = 0;
        stack[depth++] = tree.root();
        while (depth > 0) {
            int id = stack[--depth];
            Subtree node = tree.arena().get(id);
            if (node.childCount() == 0) {
                if (node.symbol() > 0 && tree.arena().sizeOf(id) > 0) {
                    out.add(new int[]{node.padding(), node.padding() + tree.arena().sizeOf(id), node.symbol()});
                }
                continue;
            }
            if (depth + node.childCount() > stack.length) {
                stack = Arrays.copyOf(stack, stack.length * 2);
            }
            for (int k = 0; k < node.childCount(); k++) {
                stack[depth++] = node.child(k);
            }
        }
        out.sort((a, b) -> Integer.compare(a[0], b[0]));
        return out;
    }

    private static boolean contentEquals(byte[] a, int aStart, byte[] b, int bStart, int length) {
        for (int i = 0; i < length; i++) {
            if (a[aStart + i] != b[bStart + i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean covered(List<TSRange> ranges, int start, int end) {
        for (TSRange range : ranges) {
            if (range.startByte() <= start && end <= range.endByte()) {
                return true;
            }
        }
        return false;
    }

    private record Case(String name, Language language, String oldSource, String newSource) {
    }

    private static List<Case> cases() {
        return Arrays.asList(
                new Case("insert into array", JSON, "[1, 2, 3]", "[1, 2, 3, 4]"),
                new Case("delete from array", JSON, "[1, 2, 3]", "[1, 3]"),
                new Case("replace in object", JSON, "{\"a\": 1}", "{\"a\": 2}"),
                new Case("edit inside string", JSON, "{\"msg\": \"hello world\"}", "{\"msg\": \"hello brave world\"}"),
                new Case("multi edit", JSON, "{\"a\": 1, \"b\": 2}", "{\"a\": 10, \"b\": 22}"),
                new Case("nested edit", JSON, "[[1, 2], [3, 4]]", "[[1, 2], [3, 44]]"),
                new Case("point edit on second line", JSON, "{\n  \"a\": 1,\n  \"b\": 2\n}", "{\n  \"a\": 1,\n  \"b\": 3\n}"),
                new Case("edit at byte zero", JSON, "[1]", "[0, 1]"),
                new Case("edit at eof", JSON, "[1]", "[12]"),
                new Case("java method body", JAVA,
                        "class A {\n    void m() {\n        int x = 1;\n    }\n}\n",
                        "class A {\n    void m() {\n        int x = 2;\n        int y = 3;\n    }\n}\n"),
                new Case("java field insert", JAVA,
                        "class A {\n    int a = 1;\n}\n",
                        "class A {\n    int a = 1;\n    int b = 2;\n}\n"));
    }

    private static TSInputEdit diffEdit(Case c) {
        byte[] oldBytes = c.oldSource().getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = c.newSource().getBytes(StandardCharsets.UTF_8);
        int prefix = 0;
        while (prefix < oldBytes.length && prefix < newBytes.length && oldBytes[prefix] == newBytes[prefix]) {
            prefix++;
        }
        int oldSuffix = 0;
        while (oldSuffix < oldBytes.length - prefix && oldSuffix < newBytes.length - prefix
                && oldBytes[oldBytes.length - 1 - oldSuffix] == newBytes[newBytes.length - 1 - oldSuffix]) {
            oldSuffix++;
        }
        int start = prefix;
        int oldEnd = oldBytes.length - oldSuffix;
        int newEnd = newBytes.length - oldSuffix;
        return TSInputEdit.of(oldBytes, newBytes, start, oldEnd, newEnd);
    }

    @Test
    void changedRangesCoverExactlyTheEditedRegionOnEveryCase() {
        for (Case c : cases()) {
            TSTree oldTree = parse(c.language(), c.oldSource());
            byte[] newBytes = c.newSource().getBytes(StandardCharsets.UTF_8);
            TSTree newTree = TSParser.parseIncremental(c.language(), oldTree,
                    Arrays.asList(diffEdit(c)), newBytes);
            TSTree full = TSParser.parse(c.language(), newBytes);
            assertEquals(full.toSExpression(), newTree.toSExpression(), c.name());

            List<TSRange> ranges = TSParser.getChangedRanges(oldTree, newTree);
            assertTrue(!ranges.isEmpty(), c.name() + " must report the edited region");
            for (int k = 1; k < ranges.size(); k++) {
                assertTrue(ranges.get(k - 1).endByte() <= ranges.get(k).startByte(),
                        c.name() + " ranges must be sorted and disjoint");
            }
            assertRangesMatchLeafDiffs(oldTree, newTree, ranges);
        }
    }

    @Test
    void identicalTreesYieldNoRanges() {
        TSTree tree = parse(JSON, "{\"a\": [1, 2], \"b\": \"x\"}");
        assertTrue(TSParser.getChangedRanges(tree, tree).isEmpty());
        TSTree reparsed = parse(JSON, "{\"a\": [1, 2], \"b\": \"x\"}");
        assertTrue(TSParser.getChangedRanges(tree, reparsed).isEmpty());
    }

    @Test
    void pureTokenContentChangeIsReported() {
        TSTree oldTree = parse(JSON, "{\"n\": 1}");
        TSTree newTree = parse(JSON, "{\"n\": 2}");
        List<TSRange> ranges = TSParser.getChangedRanges(oldTree, newTree);
        assertEquals(1, ranges.size());
        assertEquals(6, ranges.get(0).startByte());
        assertEquals(7, ranges.get(0).endByte());
    }

    @Test
    void rangePointsReportSourceCoordinates() {
        TSTree oldTree = parse(JSON, "{\n  \"a\": 1,\n  \"b\": 2\n}");
        TSTree newTree = parse(JSON, "{\n  \"a\": 1,\n  \"b\": 3\n}");
        List<TSRange> ranges = TSParser.getChangedRanges(oldTree, newTree);
        assertEquals(1, ranges.size());
        TSRange range = ranges.get(0);
        assertEquals(19, range.startByte());
        assertEquals(20, range.endByte());
        assertEquals(new TSPoint(2, 7), range.startPoint());
        assertEquals(new TSPoint(2, 8), range.endPoint());
    }

    @Test
    void whitespaceOnlyEditsOutsideTokensReportNoRanges() {
        byte[] oldBytes = "[1, 2]".getBytes(StandardCharsets.UTF_8);
        byte[] withTrailing = "[1, 2] ".getBytes(StandardCharsets.UTF_8);
        TSTree oldTree = TSParser.parse(JSON, oldBytes);
        TSTree newTree = TSParser.parseIncremental(JSON, oldTree,
                Arrays.asList(TSInputEdit.of(oldBytes, withTrailing, 6, 6, 7)), withTrailing);
        assertTrue(TSParser.getChangedRanges(oldTree, newTree).isEmpty(),
                "trailing whitespace touches no leaf and no structure");
    }

    @Test
    void incrementalTreeDiffsAgainstFullParseTreeOfSameSource() {
        byte[] oldBytes = "[1, 2, 3]".getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = "[1, 2, 3, 4]".getBytes(StandardCharsets.UTF_8);
        TSTree oldTree = TSParser.parse(JSON, oldBytes);
        TSTree incremental = TSParser.parseIncremental(JSON, oldTree,
                Arrays.asList(TSInputEdit.of(oldBytes, newBytes, 8, 8, 11)), newBytes);
        TSTree full = TSParser.parse(JSON, newBytes);
        List<TSRange> ranges = TSParser.getChangedRanges(incremental, full);
        assertTrue(ranges.isEmpty(),
                "the incremental tree must be content-identical to the full reparse, diff was " + ranges);
    }

    @Test
    void changedRangesValidatesInputs() {
        TSTree tree = parse(JSON, "[1]");
        Language other = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");
        TSTree otherTree = TSParser.parse(other, "[1]".getBytes(StandardCharsets.UTF_8));
        assertThrows(TreeSitterException.class, () -> TSParser.getChangedRanges(null, tree));
        assertThrows(TreeSitterException.class, () -> TSParser.getChangedRanges(tree, null));
        assertThrows(TreeSitterException.class, () -> TSParser.getChangedRanges(tree, otherTree));
    }
}
