package io.nop.treesitter.subtree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Subtree value semantics: record value equality over every field and the
 * parent-arity invariant (dense child slots, derived child count).
 */
class SubtreeTest {

    @Test
    void valueEqualityCoversEveryField() {
        Subtree a = node(10, 11);
        Subtree b = node(10, 11);
        Subtree diffState = new Subtree(9, 0, 10, 11, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0);
        Subtree diffSymbol = new Subtree(0, 9, 10, 11, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0);
        Subtree diffChild = new Subtree(0, 0, 10, 99, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0);
        Subtree diffExtra = new Subtree(0, 0, 10, 11, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 1, 0);
        Subtree diffPadding = new Subtree(0, 0, 10, 11, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 9);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, diffState);
        assertNotEquals(a, diffSymbol);
        assertNotEquals(a, diffChild);
        assertNotEquals(a, diffExtra);
        assertNotEquals(a, diffPadding);
    }

    @Test
    void childCountAndChildAccessFollowDensity() {
        Subtree empty = leaf(0);
        Subtree one = node(7);
        Subtree two = node(7, 8);
        Subtree three = node(7, 8, 9);
        Subtree four = node(7, 8, 9, 10);
        Subtree eight = node(1, 2, 3, 4, 5, 6, 7, 8);

        assertEquals(0, empty.childCount());
        assertEquals(1, one.childCount());
        assertEquals(2, two.childCount());
        assertEquals(3, three.childCount());
        assertEquals(4, four.childCount());
        assertEquals(8, eight.childCount());

        assertEquals(7, one.child(0));
        assertEquals(7, two.child(0));
        assertEquals(8, two.child(1));
        assertEquals(9, three.child(2));
        assertEquals(10, four.child(3));
        assertEquals(8, eight.child(7));
    }

    @Test
    void childAccessOutOfRangeThrows() {
        Subtree node = node(7, 8);
        assertThrows(IndexOutOfBoundsException.class, () -> node.child(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> node.child(2));
        assertThrows(IndexOutOfBoundsException.class, () -> node.child(Subtree.MAX_CHILDREN));
    }

    @Test
    void gapInChildSlotsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Subtree(0, 0, Subtree.NO_ID, 7, 8, Subtree.NO_ID,
                        Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Subtree(0, 0, 7, Subtree.NO_ID, 8, Subtree.NO_ID,
                        Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Subtree(0, 0, 7, 8, Subtree.NO_ID, 9,
                        Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Subtree(0, 0, 1, 2, 3, 4, 5, Subtree.NO_ID, 7, 8, 0, 0));
    }

    private static Subtree leaf(int symbol) {
        return new Subtree(0, symbol, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID,
                Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0);
    }

    private static Subtree node(int... children) {
        int c0 = children.length > 0 ? children[0] : Subtree.NO_ID;
        int c1 = children.length > 1 ? children[1] : Subtree.NO_ID;
        int c2 = children.length > 2 ? children[2] : Subtree.NO_ID;
        int c3 = children.length > 3 ? children[3] : Subtree.NO_ID;
        int c4 = children.length > 4 ? children[4] : Subtree.NO_ID;
        int c5 = children.length > 5 ? children[5] : Subtree.NO_ID;
        int c6 = children.length > 6 ? children[6] : Subtree.NO_ID;
        int c7 = children.length > 7 ? children[7] : Subtree.NO_ID;
        return new Subtree(0, 0, c0, c1, c2, c3, c4, c5, c6, c7, 0, 0);
    }
}