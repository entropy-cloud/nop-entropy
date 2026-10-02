package io.nop.core.lang.xml.ext;

import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStyleMap {

    @Test
    public void testParseStyleString() {
        StyleMap map = StyleMap.parse("color:red;font-size:12px");
        assertEquals(2, map.size());
        assertEquals("red", map.get("color"));
        assertEquals("12px", map.get("font-size"));
        assertTrue(map.contains("color"));
        assertTrue(map.keySet().contains("color"));
    }

    @Test
    public void testParseNullAndEmpty() {
        assertEquals(0, StyleMap.parse(null).size());
        assertEquals(0, StyleMap.parse("").size());
    }

    @Test
    public void testToStringRoundTrip() {
        StyleMap map = StyleMap.parse("color:red;font-size:12px");
        StyleMap reparsed = StyleMap.parse(map.toString());
        assertEquals(map.size(), reparsed.size());
        assertEquals("red", reparsed.get("color"));
        assertEquals("12px", reparsed.get("font-size"));
    }

    @Test
    public void testGetNumberStripsUnit() {
        StyleMap map = StyleMap.parse("width:100px;height:2.5em");
        assertEquals(100, map.getNumber("width").intValue());
        assertEquals(2.5, map.getNumber("height").doubleValue(), 0.0001);
        assertNull(map.getNumber("missing"));
    }

    @Test
    public void testAddRemoveAndRemoveAll() {
        StyleMap map = new StyleMap();
        map.add("a", "1");
        map.addAll(java.util.Map.of("b", "2"));
        assertEquals(2, map.size());

        map.remove("a");
        assertFalse(map.contains("a"));

        map.removeAll(Arrays.asList("b"));
        assertEquals(0, map.size());
        assertNull(map.get("b"));
    }

    @Test
    public void testSyncFromAndToNode() {
        XNode node = XNode.make("div");
        node.setAttr("style", "color:red");
        node.setAttr("other", "keep");

        StyleMap map = new StyleMap();
        map.syncFromNode(node);
        assertEquals("red", map.get("color"));

        // 修改后 sync 回节点
        map.add("font-size", "12px");
        map.syncToNode(node);
        assertEquals("color:red;font-size:12px", node.attrText("style"));
        assertEquals("keep", node.attrText("other"), "unrelated attrs should stay untouched");
    }

    @Test
    public void testSyncToNodeRemovesEmptyStyle() {
        XNode node = XNode.make("div");
        node.setAttr("style", "color:red");

        StyleMap map = StyleMap.makeFromNode(node);
        assertEquals("red", map.get("color"));
        // makeFromNode 会缓存 extension，再次获取返回同一实例
        assertSame(map, StyleMap.getFromNode(node));

        map.remove("color");
        map.syncToNode(node);
        assertNull(node.attrText("style"), "empty style map should remove style attr");
    }
}
