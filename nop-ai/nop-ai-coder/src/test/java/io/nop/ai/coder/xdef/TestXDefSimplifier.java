package io.nop.ai.coder.xdef;

import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestXDefSimplifier {

    @Test
    public void testSimplifyRemovesXdefAttrs() {
        XNode node = XNode.make("prop");
        node.setAttr("name", "code");
        node.setAttr("xdef:value", "string");
        node.setAttr("xdef:name", "MyProp");
        node.setAttr("displayName", "属性");

        XDefSimplifier.INSTANCE.simplify(node);

        assertNull(node.attrText("xdef:value"), "xdef:value should be removed");
        assertNull(node.attrText("xdef:name"), "xdef:name should be removed");
        assertEquals("string", node.getContentValue());
        assertEquals("code", node.attrText("name"));
    }

    @Test
    public void testSimplifyRemovesXdefChildren() {
        XNode node = XNode.make("defs");
        XNode prop = XNode.make("prop");
        prop.setAttr("name", "a");
        XNode xdefChild = XNode.make("xdef:child");
        xdefChild.setAttr("xdef:key-attr", "name");
        prop.appendChild(xdefChild);
        node.appendChild(prop);
        node.appendChild(XNode.make("xdef:unknown"));

        XDefSimplifier.INSTANCE.simplify(node);

        assertFalse(node.getChildren().stream().anyMatch(c -> c.getTagName().startsWith("xdef:")),
                "xdef:* children should be removed");
        assertNull(prop.childByTag("xdef:child"), "xdef:* nested children should also be removed");
    }

    @Test
    public void testSimplifyRecursesIntoChildren() {
        XNode root = XNode.make("root");
        XNode child = XNode.make("child");
        child.setAttr("xdef:value", "abc");
        root.appendChild(child);

        XDefSimplifier.INSTANCE.simplify(root);

        assertEquals("abc", child.getContentValue());
        assertNull(child.attrText("xdef:value"));
    }

    @Test
    public void testSimplifyNullReturnsNull() {
        assertNull(XDefSimplifier.INSTANCE.simplify(null));
    }

    @Test
    public void testSimplifyEmptyNode() {
        XNode node = XNode.make("empty");
        XDefSimplifier.INSTANCE.simplify(node);
        assertEquals("empty", node.getTagName());
        assertEquals(0, node.getChildren().size());
    }
}
