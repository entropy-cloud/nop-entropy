package io.nop.treesitter.parser;

import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;
import io.nop.treesitter.subtree.SubtreeArena;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error-node rendering (roadmap item 11 phase 1): hand-built arena trees with
 * ERROR composites, invisible ERROR_REPEAT chains, missing-token leaves and
 * lexer error leaves render in both sexp forms exactly as the C runtime's
 * {@code ts_node_string} conventions dictate.
 */
class ErrorNodeRenderingTest {

    private static final Language LANGUAGE = Language.fromClasspath("/grammars/json/tree-sitter-json-blob.bin");

    private static final int DOCUMENT = 15;
    private static final int NUMBER = 10;

    private static int numberLeaf(SubtreeArena arena) {
        int id = arena.allocate(0, NUMBER, 0, 0);
        arena.setSize(id, 1);
        return id;
    }

    @Test
    void errorCompositeRendersAsErrorWithChildren() {
        SubtreeArena arena = new SubtreeArena();
        int error = arena.allocate(0, LANGUAGE.builtinErrorSymbol(), 0, 0, numberLeaf(arena));
        int root = arena.allocate(0, DOCUMENT, 0, 0, error);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        assertEquals("(document\n  (ERROR\n    (number)))", tree.toSExpression());
        assertEquals("(document (ERROR (number)))", tree.toSexpString(false));
    }

    @Test
    void errorRepeatIsInvisibleAndFlattensThrough() {
        SubtreeArena arena = new SubtreeArena();
        int errorLeaf = arena.allocateErrorLeaf(LANGUAGE.builtinErrorSymbol(), 0, 'x');
        arena.setSize(errorLeaf, 1);
        int repeat = arena.allocate(0, LANGUAGE.builtinErrorRepeatSymbol(), 0, 0, errorLeaf);
        int root = arena.allocate(0, DOCUMENT, 0, 0, repeat);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        assertEquals("(document\n  (UNEXPECTED 'x'))", tree.toSExpression());
        assertEquals("(document (UNEXPECTED 'x'))", tree.toSexpString(false));
    }

    @Test
    void missingAnonymousTokenRendersQuoted() {
        SubtreeArena arena = new SubtreeArena();
        int comma = LANGUAGE.symbolId(",", false);
        int missing = arena.allocateMissing(comma, 0);
        int root = arena.allocate(0, DOCUMENT, 0, 0, missing);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        assertEquals("(document\n  (MISSING \",\"))", tree.toSExpression());
        assertEquals("(document (MISSING \",\"))", tree.toSexpString(false));
    }

    @Test
    void missingNamedTokenRendersBare() {
        SubtreeArena arena = new SubtreeArena();
        int missing = arena.allocateMissing(NUMBER, 0);
        int root = arena.allocate(0, DOCUMENT, 0, 0, missing);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        assertEquals("(document\n  (MISSING number))", tree.toSExpression());
        assertEquals("(document (MISSING number))", tree.toSexpString(false));
    }

    @Test
    void lexerErrorLeafEscapesSpecialCharacters() {
        SubtreeArena arena = new SubtreeArena();
        int newlineLeaf = arena.allocateErrorLeaf(LANGUAGE.builtinErrorSymbol(), 0, '\n');
        arena.setSize(newlineLeaf, 1);
        int nonPrintableLeaf = arena.allocateErrorLeaf(LANGUAGE.builtinErrorSymbol(), 0, 0xE9);
        arena.setSize(nonPrintableLeaf, 1);
        int root = arena.allocate(0, DOCUMENT, 0, 0, newlineLeaf, nonPrintableLeaf);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        assertEquals("(document (UNEXPECTED '\\n') (UNEXPECTED 233))", tree.toSexpString(false));
    }

    @Test
    void snapshotPreservesErrorAndMissingMarks() {
        SubtreeArena arena = new SubtreeArena();
        int errorLeaf = arena.allocateErrorLeaf(LANGUAGE.builtinErrorSymbol(), 0, 'x');
        arena.setSize(errorLeaf, 1);
        int repeat = arena.allocate(0, LANGUAGE.builtinErrorRepeatSymbol(), 0, 0, errorLeaf);
        int missing = arena.allocateMissing(NUMBER, 0);
        int root = arena.allocate(0, DOCUMENT, 0, 0, repeat, missing);
        TSTree tree = TSTree.snapshot(LANGUAGE, arena, root, new byte[0]);

        SubtreeArena own = tree.arena();
        int snapshotRoot = tree.root();
        assertEquals(LANGUAGE.builtinErrorRepeatSymbol(), own.get(snapshotRoot).child(0) >= 0
                ? own.get(own.get(snapshotRoot).child(0)).symbol() : -1);
        assertFalse(own.isMissing(own.get(snapshotRoot).child(0)));
        assertTrue(own.isMissing(own.get(snapshotRoot).child(1)));
        assertEquals('x', own.lookaheadCharOf(own.get(own.get(snapshotRoot).child(0)).child(0)));
        assertEquals("(document (UNEXPECTED 'x') (MISSING number))", tree.toSexpString(false));
    }

    @Test
    void freedSlotReuseDoesNotLeakMissingMark() {
        SubtreeArena arena = new SubtreeArena();
        int missing = arena.allocateMissing(NUMBER, 0);
        arena.free(missing);
        int replacement = arena.allocate(0, NUMBER, 0, 0);
        assertEquals(missing, replacement, "the freed slot is reused");
        assertFalse(arena.isMissing(replacement), "the missing mark must not leak into the reused slot");
        assertEquals(0, arena.lookaheadCharOf(replacement));
    }
}
