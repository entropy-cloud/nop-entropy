package io.nop.core.lang.utils;

import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestXNodeMergeHelper {

    @Test
    public void testContainsAllAttrs() {
        XNode a = XNode.make("a");
        a.setAttr("x", "1");
        a.setAttr("y", "2");

        XNode b = XNode.make("b");
        b.setAttr("x", "1");
        assertTrue(XNodeMergeHelper.containsAllAttrs(a, b), "a has all attrs of b");

        b.setAttr("x", "9");
        assertFalse(XNodeMergeHelper.containsAllAttrs(a, b), "different value should not match");

        XNode c = XNode.make("c");
        c.setAttr("x", "1");
        c.setAttr("z", "3");
        assertFalse(XNodeMergeHelper.containsAllAttrs(a, c), "missing attr should not match");
    }

    @Test
    public void testMergeIfAbsentAttrsNotOverwritten() {
        XNode a = XNode.make("a");
        a.setAttr("x", "orig");
        a.setAttr("only-a", "1");

        XNode b = XNode.make("b");
        b.setAttr("x", "from-b");
        b.setAttr("only-b", "2");

        XNode merged = XNodeMergeHelper.mergeIfAbsent(a, b);
        assertEquals("orig", merged.getAttr("x"), "existing attr should not be overwritten");
        assertEquals("2", merged.getAttr("only-b"), "missing attr should be merged in");
        assertEquals("1", merged.getAttr("only-a"));
    }

    @Test
    public void testMergeIfAbsentContentFillsEmptyBody() {
        XNode a = XNode.make("a");
        XNode b = XNode.make("b");
        b.content("text");
        XNodeMergeHelper.mergeIfAbsent(a, b);
        assertEquals("text", a.contentText(), "empty target should receive source content");
    }

    @Test
    public void testMergeIfAbsentKeepsExistingContent() {
        XNode a = XNode.make("a");
        a.content("existing");
        XNode b = XNode.make("b");
        b.content("incoming");
        XNodeMergeHelper.mergeIfAbsent(a, b);
        assertEquals("existing", a.contentText(), "existing content should not be overwritten");
    }

    @Test
    public void testMergeIfAbsentAppendsMissingChildren() {
        XNode a = XNode.make("a");
        a.appendChild(XNode.make("keep"));

        XNode b = XNode.make("b");
        b.appendChild(XNode.make("keep"));
        XNode newChild = XNode.make("extra");
        b.appendChild(newChild);

        XNodeMergeHelper.mergeIfAbsent(a, b);
        assertEquals(2, a.getChildCount());
        assertEquals("keep", a.child(0).getTagName());
        assertEquals("extra", a.child(1).getTagName());
    }

    @Test
    public void testMergeIfAbsentMergesSameTagChildren() {
        XNode a = XNode.make("a");
        XNode aChild = XNode.make("item");
        aChild.setAttr("id", "1");
        a.appendChild(aChild);

        XNode b = XNode.make("b");
        XNode bChild = XNode.make("item");
        bChild.setAttr("id", "1");
        bChild.setAttr("extra", "e");
        b.appendChild(bChild);

        XNodeMergeHelper.mergeIfAbsent(a, b);
        assertEquals(1, a.getChildCount(), "same-tag child should merge, not duplicate");
        assertEquals("1", a.child(0).getAttr("id"));
        assertEquals("e", a.child(0).getAttr("extra"), "nested attrs merged if absent");
    }

    @Test
    public void testNormalizeMergedRemovesMultipleAttr() {
        XNode node = XNode.make("root");
        XNode child = XNode.make("item");
        child.setAttr("xml:multiple", "true");
        node.appendChild(child);

        XNodeMergeHelper.normalizeMerged(node);
        assertNull(node.child(0).getAttr("xml:multiple"), "xml:multiple marker should be removed");
    }
}
