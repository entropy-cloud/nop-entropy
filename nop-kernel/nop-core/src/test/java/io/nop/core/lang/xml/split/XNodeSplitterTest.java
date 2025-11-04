package io.nop.core.lang.xml.split;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XPathProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XNodeSplitterTest {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        XPathProvider.registerInstance(new XPathProvider() {
            @Override
            public IXSelector<XNode> compile(String xpath) {
                return new IXSelector<>() {
                    @Override
                    public Object select(XNode node) {
                        return node.childByTag(xpath);
                    }

                    @Override
                    public void updateSelected(XNode node, Object value) {

                    }

                    @Override
                    public Collection<?> selectAll(XNode node) {
                        return node.childrenByTag(xpath);
                    }
                };
            }
        });
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void testSplitNullNode() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                XNodeSplitter.split(null, 100, "child");
            }
        });
    }

    @Test
    void testSplitWithInvalidMaxSize() {
        XNode node = XNode.parse("<root/>");
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                XNodeSplitter.split(node, 0, "child");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                XNodeSplitter.split(node, -1, "child");
            }
        });
    }

    @Test
    void testSplitSimpleNodeNoChildren() {
        XNode node = XNode.parse("<root attr1=\"value1\">simple content</root>");

        List<XNode> result = XNodeSplitter.split(node, 10, "child");
        assertEquals(1, result.size());
        assertEquals(node.xml(), result.get(0).xml());
    }

    @Test
    void testSplitWithSingleChildUnderMaxSize() {
        XNode root = XNode.parse("<root><child>content</child></root>");

        List<XNode> result = XNodeSplitter.split(root, 100, "child");
        assertEquals(1, result.size());
        assertEquals(root.xml(), result.get(0).xml());
    }

    @Test
    void testSplitWithMultipleChildrenOverMaxSize() {
        XNode root = XNode.parse("<root><child>content-0</child><child>content-1</child><child>content-2</child></root>");

        // Calculate size of one child
        XNode firstChild = root.child(0);
        int childSize = XNodeSplitter.calcNodeSize(firstChild);

        // Set max size to be smaller than one child's size
        List<XNode> result = XNodeSplitter.split(root, childSize - 1, null);

        assertEquals(3, result.size());
        for (int i = 0; i < 3; i++) {
            XNode splitNode = result.get(i);
            assertEquals("root", splitNode.getTagName());
            assertEquals(1, splitNode.getChildCount());
            assertEquals("content-" + i, splitNode.child(0).contentText());
        }
    }

    @Test
    void testSplitWithNestedChildren() {
        XNode root = XNode.parse("<root><parent><child>content-0</child><child>content-1</child><child>content-2</child></parent></root>");

        // Calculate size of parent with one child
        XNode parent = root.child(0);
        int parentWithOneChildSize = XNodeSplitter.calcNodeSize(parent) +
                XNodeSplitter.calcNodeSize(parent.child(0));

        List<XNode> result = XNodeSplitter.split(root, parentWithOneChildSize, "parent");

        assertEquals(3, result.size());
        for (int i = 0; i < 3; i++) {
            XNode splitNode = result.get(i);
            assertEquals("root", splitNode.getTagName());
            assertEquals(1, splitNode.getChildCount());
            assertEquals("parent", splitNode.child(0).getTagName());
            assertEquals(1, splitNode.child(0).getChildCount());
            assertEquals("content-" + i, splitNode.child(0).child(0).contentText());
        }
    }

    @Test
    void testSplitWithCommentsAndAttributes() {
        XNode root = XNode.parse("<!-- Root comment --><root attr1=\"value1\"><!-- Child 1 comment --><child attr2=\"value2\">content1</child><child>content2</child></root>");

        // Calculate size of first child with all its metadata
        XNode child1 = root.child(0);
        int child1Size = XNodeSplitter.calcCommentSize(child1.getComment()) +
                XNodeSplitter.calcNodeSize(child1);

        List<XNode> result = XNodeSplitter.split(root, child1Size, null);

        assertEquals(2, result.size());

        // Verify first split contains only first child
        XNode firstSplit = result.get(0);
        assertEquals("root", firstSplit.getTagName());
        assertEquals("Root comment", firstSplit.getComment());
        assertEquals("value1", firstSplit.attrText("attr1"));
        assertEquals(1, firstSplit.getChildCount());
        assertEquals("Child 1 comment", firstSplit.child(0).getComment());
        assertEquals("content1", firstSplit.child(0).contentText());

        // Verify second split contains only second child
        XNode secondSplit = result.get(1);
        assertEquals("root", secondSplit.getTagName());
        assertEquals(null, secondSplit.getComment());
        assertEquals("value1", secondSplit.attrText("attr1"));
        assertEquals(1, secondSplit.getChildCount());
        assertEquals("content2", secondSplit.child(0).contentText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "  ", "invalid-xpath"})
    void testSplitWithInvalidSplitPath(String splitPath) {
        XNode root = XNode.parse("<root><child>content</child></root>");

        List<XNode> result = XNodeSplitter.split(root, 10, splitPath);

        // Should not split since split path is invalid
        assertEquals(1, result.size());
        assertEquals(root.xml(), result.get(0).xml());
    }

    @Test
    void testCalcNodeSize() {
        XNode node = XNode.parse("<test attr1=\"value1\" attr2=\"value2\">content</test>");

        int expectedSize = XNodeSplitter.calcTagSize("test", false) +
                XNodeSplitter.calcAttrsSize(node.attrValueLocs()) +
                XNodeSplitter.calcContentSize(node.content());

        assertEquals(expectedSize, XNodeSplitter.calcNodeSize(node));
    }

    @Test
    void testCalcDescendantsSize() {
        XNode root = XNode.parse("<root><child1>content1</child1><child2>content2</child2></root>");

        int expectedSize = XNodeSplitter.calcNodeSize(root.child(0)) +
                XNodeSplitter.calcNodeSize(root.child(1));

        assertEquals(expectedSize, XNodeSplitter.calcDescendantsSize(root));
    }
}