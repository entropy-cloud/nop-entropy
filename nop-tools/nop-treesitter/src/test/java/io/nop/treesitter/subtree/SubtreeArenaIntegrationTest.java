package io.nop.treesitter.subtree;

import io.nop.treesitter.util.SymbolTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Downstream-consumer integration proof (Anti-Hollow / Wiring rule).
 *
 * <p>Builds a depth-3, mixed-arity tree where every node type comes from a
 * {@link SymbolTable} (not a parallel hand-rolled map), then navigates it purely
 * through the {@link SubtreeArena} public API — root → children → siblings →
 * parent — and asserts the structural shape end to end. This exercises every
 * component of the arena public surface the LR(1) parser plan consumes.</p>
 */
class SubtreeArenaIntegrationTest {

    private final SymbolTable symbols = new SymbolTable();

    @Test
    void nestedTreeBuildsAndNavigatesThroughArenaApi() {
        int document = symbols.intern("document");
        int array = symbols.intern("array");
        int object = symbols.intern("object");
        int number = symbols.intern("number");
        int string = symbols.intern("string");
        int trueSym = symbols.intern("true");
        int nullSym = symbols.intern("null");
        assertNotEquals(array, object);

        SubtreeArena arena = new SubtreeArena();

        int num1 = arena.allocate(0, number, 0, 0);
        int num2 = arena.allocate(0, number, 0, 0);
        int str1 = arena.allocate(0, string, 0, 0);
        int trueNode = arena.allocate(0, trueSym, 0, 0);
        int nullNode = arena.allocate(0, nullSym, 0, 0);

        int array2 = arena.allocate(1, array, 0, 0, trueNode, num2);
        int array1 = arena.allocate(1, array, 0, 0, num1, array2, str1);
        int root = arena.allocate(2, document, 0, 0, array1);

        assertEquals(8, arena.size());

        Subtree rootNode = arena.get(root);
        assertEquals(document, rootNode.symbol());
        assertEquals("document", symbols.resolve(rootNode.symbol()));
        assertEquals(1, rootNode.childCount());

        int array1Id = rootNode.child(0);
        Subtree array1Node = arena.get(array1Id);
        assertEquals(array, array1Node.symbol());
        assertEquals(3, array1Node.childCount());

        int num1Id = array1Node.child(0);
        int array2Id = array1Node.child(1);
        int str1Id = array1Node.child(2);
        assertEquals(number, arena.get(num1Id).symbol());
        assertEquals(array, arena.get(array2Id).symbol());
        assertEquals(string, arena.get(str1Id).symbol());

        assertEquals(array1Id, arena.parentOf(num1Id));
        assertEquals(array1Id, arena.parentOf(array2Id));
        assertEquals(array1Id, arena.parentOf(str1Id));
        assertEquals(root, arena.parentOf(array1Id));
        assertEquals(Subtree.NO_ID, arena.parentOf(root));

        Subtree array2Node = arena.get(array2Id);
        int trueId = array2Node.child(0);
        int num2Id = array2Node.child(1);
        assertEquals(trueSym, arena.get(trueId).symbol());
        assertEquals(number, arena.get(num2Id).symbol());
        assertEquals(2, array2Node.childCount());
        assertEquals(array2Id, arena.parentOf(trueId));
        assertEquals(array2Id, arena.parentOf(num2Id));

        assertEquals(0, arena.get(trueId).childCount());
        assertEquals(0, arena.get(num1Id).childCount());
    }
}
