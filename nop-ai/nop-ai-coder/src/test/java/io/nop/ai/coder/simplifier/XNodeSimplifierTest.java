package io.nop.ai.coder.simplifier;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XNodeSimplifierTest {

    @Test
    void testSimplify_nullInput() {
        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("attr1"), Set.of("id"));
        assertNull(simplifier.simplify(null));
    }

    @Test
    void testSimplify_emptyNode_noKeysToKeep() {
        XNode input = XNode.make("root");
        XNodeSimplifier simplifier = new XNodeSimplifier(Collections.emptySet(), Collections.emptySet());
        assertNull(simplifier.simplify(input));
    }

    @Test
    void testSimplify_nodeWithTagNameToKeep() {
        XNode input = XNode.make("important");
        input.setAttr("attr1", "value1");

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("important"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertEquals("important", result.getTagName());
        assertFalse(result.hasAttr());
    }

    @Test
    void testSimplify_nodeWithAttrToKeep() {
        XNode input = XNode.make("root");
        input.setAttr("importantAttr", "value1");
        input.setAttr("otherAttr", "value2");

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("importantAttr"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertTrue(result.hasAttr("importantAttr"));
        assertFalse(result.hasAttr("otherAttr"));
    }

    @Test
    void testSimplify_nodeWithPositioningAttr() {
        XNode input = XNode.make("root");
        input.setAttr("id", "123");
        input.setAttr("otherAttr", "value2");
        input.setAttr("a", "1");

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("a"), Set.of("id"));
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertTrue(result.hasAttr("id"));
        assertFalse(result.hasAttr("otherAttr"));
    }

    @Test
    void testSimplify_nodeWithChildren() {
        XNode input = XNode.make("root");
        XNode child1 = XNode.make("child1");
        child1.setAttr("importantAttr", "value1");
        input.appendChild(child1);

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("importantAttr"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertEquals(1, result.getChildCount());
        XNode simplifiedChild = result.childByIndex(0);
        assertEquals("child1", simplifiedChild.getTagName());
        assertTrue(simplifiedChild.hasAttr("importantAttr"));
    }

    @Test
    void testSimplify_nestedChildren() {
        XNode input = XNode.make("root");
        XNode child1 = XNode.make("child1");
        XNode grandchild = XNode.make("grandchild");
        grandchild.setAttr("importantAttr", "value1");
        child1.appendChild(grandchild);
        input.appendChild(child1);

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("importantAttr"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertEquals(1, result.getChildCount());
        XNode simplifiedChild = result.childByIndex(0);
        assertEquals(1, simplifiedChild.getChildCount());
        XNode simplifiedGrandchild = simplifiedChild.childByIndex(0);
        assertTrue(simplifiedGrandchild.hasAttr("importantAttr"));
    }

    @Test
    void testSimplify_childrenFilteredOut() {
        XNode input = XNode.make("root");
        XNode child1 = XNode.make("child1");
        child1.setAttr("unimportantAttr", "value1");
        input.appendChild(child1);

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("importantAttr"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNull(result);
    }

    @Test
    void testSimplify_locationPreserved() {
        XNode input = XNode.make("root");
        input.setLocation(SourceLocation.fromPath("test.xml"));

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("root"), Set.of());
        XNode result = simplifier.simplify(input);

        assertNotNull(result);
        assertEquals("test.xml", result.getLocation().getPath());
    }

    @Test
    void testSimplifyAttrs_onlyKeepsSpecifiedAttributes() {
        XNode input = XNode.make("root");
        input.setAttr("keep1", "value1");
        input.setAttr("keep2", "value2");
        input.setAttr("discard", "value3");

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("keep1", "keep2"), Set.of());
        XNode result = XNode.make("root");
        simplifier.simplifyAttrs(input, result);

        assertTrue(result.hasAttr("keep1"));
        assertTrue(result.hasAttr("keep2"));
        assertFalse(result.hasAttr("discard"));
    }

    @Test
    void testSimplifyChildren_filtersNullResults() {
        XNode input = XNode.make("parent");
        XNode child1 = XNode.make("child1");
        child1.setAttr("keep", "value1");
        XNode child2 = XNode.make("child2"); // won't be kept
        input.appendChild(child1);
        input.appendChild(child2);

        XNodeSimplifier simplifier = new XNodeSimplifier(Set.of("keep"), Set.of());
        XNode result = simplifier.simplify(input);

        assertEquals(1, result.getChildren().size());
        assertEquals("child1", result.childByIndex(0).getTagName());
    }


}