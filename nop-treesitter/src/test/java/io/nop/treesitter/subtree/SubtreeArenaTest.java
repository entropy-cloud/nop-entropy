package io.nop.treesitter.subtree;

import io.nop.treesitter.TreeSitterException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SubtreeArena core: allocation, free-list slot reuse, geometric growth,
 * value consistency, parent linkage and no-silent-skip invalid-id handling.
 */
class SubtreeArenaTest {

    @Test
    void allocateReturnsMonotonicallyIncreasingIdsUntilFirstFree() {
        SubtreeArena arena = new SubtreeArena();
        int first = arena.allocate(1, 10, 0, 0);
        int second = arena.allocate(2, 20, 0, 0);
        int third = arena.allocate(3, 30, 0, 0);
        assertEquals(0, first);
        assertEquals(1, second);
        assertEquals(2, third);
        assertEquals(3, arena.size());
    }

    @Test
    void freeThenAllocateReusesSlotWithoutGrowing() {
        SubtreeArena arena = new SubtreeArena();
        int a = arena.allocate(1, 10, 0, 0);
        int b = arena.allocate(2, 20, 0, 0);
        int c = arena.allocate(3, 30, 0, 0);
        int capacityBeforeFree = arena.capacity();

        arena.free(b);
        assertFalse(arena.isLive(b));
        assertTrue(arena.isLive(a));
        assertTrue(arena.isLive(c));

        int reused = arena.allocate(4, 40, 0, 0);
        assertEquals(b, reused);
        assertEquals(capacityBeforeFree, arena.capacity(), "reuse must not grow the arena");
        assertEquals(3, arena.size());
    }

    @Test
    void freeListIsLifoAndReusesAllFreedSlots() {
        SubtreeArena arena = new SubtreeArena();
        int a = arena.allocate(1, 10, 0, 0);
        int b = arena.allocate(2, 20, 0, 0);
        int c = arena.allocate(3, 30, 0, 0);
        arena.free(a);
        arena.free(c);
        assertEquals(c, arena.allocate(4, 40, 0, 0));
        assertEquals(a, arena.allocate(5, 50, 0, 0));
        assertEquals(3, arena.size());
    }

    @Test
    void growPreservesPriorContents() {
        SubtreeArena arena = new SubtreeArena();
        int first = arena.allocate(1, 10, 3, 7);
        int initialCapacity = arena.capacity();
        int last = -1;
        for (int i = 1; i < 40; i++) {
            last = arena.allocate(i, 10 + i, 0, 0);
        }
        assertTrue(arena.capacity() > initialCapacity, "growth must have happened");
        assertEquals(40, arena.size());
        assertTrue(arena.capacity() >= 40);

        assertEquals(new Subtree(1, 10, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 3, 7),
                arena.get(first));
        assertEquals(new Subtree(39, 49, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, Subtree.NO_ID, 0, 0),
                arena.get(last));
    }

    @Test
    void getReturnsConsistentFieldValues() {
        SubtreeArena arena = new SubtreeArena();
        int childA = arena.allocate(1, 10, 0, 0);
        int childB = arena.allocate(2, 20, 0, 0);
        int id = arena.allocate(5, 42, 1, 9, childA, childB);
        Subtree node = arena.get(id);
        assertEquals(new Subtree(5, 42, childA, childB, Subtree.NO_ID, Subtree.NO_ID, 1, 9), node);
        assertEquals(2, node.childCount());
    }

    @Test
    void getOnNeverAllocatedIdThrows() {
        SubtreeArena arena = new SubtreeArena();
        arena.allocate(1, 10, 0, 0);
        assertThrows(TreeSitterException.class, () -> arena.get(1));
        assertThrows(TreeSitterException.class, () -> arena.get(5));
        assertThrows(TreeSitterException.class, () -> arena.get(-1));
    }

    @Test
    void getOnFreedIdThrows() {
        SubtreeArena arena = new SubtreeArena();
        int id = arena.allocate(1, 10, 0, 0);
        arena.free(id);
        assertThrows(TreeSitterException.class, () -> arena.get(id));
    }

    @Test
    void freeOnNeverAllocatedIdThrows() {
        SubtreeArena arena = new SubtreeArena();
        arena.allocate(1, 10, 0, 0);
        assertThrows(TreeSitterException.class, () -> arena.free(1));
        assertThrows(TreeSitterException.class, () -> arena.free(-1));
    }

    @Test
    void freeTwiceThrows() {
        SubtreeArena arena = new SubtreeArena();
        int id = arena.allocate(1, 10, 0, 0);
        arena.free(id);
        assertThrows(TreeSitterException.class, () -> arena.free(id));
    }

    @Test
    void allocateWithNonLiveChildThrows() {
        SubtreeArena arena = new SubtreeArena();
        int child = arena.allocate(1, 10, 0, 0);
        arena.free(child);
        assertThrows(TreeSitterException.class, () -> arena.allocate(2, 20, 0, 0, child));
        assertThrows(TreeSitterException.class, () -> arena.allocate(2, 20, 0, 0, 99));
        assertThrows(TreeSitterException.class, () -> arena.allocate(2, 20, 0, 0, Subtree.NO_ID));
    }

    @Test
    void allocateWithTooManyChildrenThrows() {
        SubtreeArena arena = new SubtreeArena();
        int[] children = new int[Subtree.MAX_CHILDREN + 1];
        for (int i = 0; i < children.length; i++) {
            children[i] = arena.allocate(1, 10, 0, 0);
        }
        assertThrows(IllegalArgumentException.class,
                () -> arena.allocate(2, 20, 0, 0, children));
    }

    @Test
    void parentLinkIsRecordedOnAllocate() {
        SubtreeArena arena = new SubtreeArena();
        int childA = arena.allocate(1, 10, 0, 0);
        int childB = arena.allocate(2, 20, 0, 0);
        assertEquals(Subtree.NO_ID, arena.parentOf(childA));
        assertEquals(Subtree.NO_ID, arena.parentOf(childB));

        int parent = arena.allocate(3, 30, 0, 0, childA, childB);
        assertEquals(parent, arena.parentOf(childA));
        assertEquals(parent, arena.parentOf(childB));
        assertEquals(Subtree.NO_ID, arena.parentOf(parent));
    }

    @Test
    void parentLinkIsRefreshedAfterSlotReuse() {
        SubtreeArena arena = new SubtreeArena();
        int child = arena.allocate(1, 10, 0, 0);
        int firstParent = arena.allocate(2, 20, 0, 0, child);
        assertEquals(firstParent, arena.parentOf(child));

        arena.free(child);
        arena.free(firstParent);
        int newChild = arena.allocate(3, 30, 0, 0);
        int newParent = arena.allocate(4, 40, 0, 0, newChild);
        assertEquals(newParent, arena.parentOf(newChild));
    }
}
