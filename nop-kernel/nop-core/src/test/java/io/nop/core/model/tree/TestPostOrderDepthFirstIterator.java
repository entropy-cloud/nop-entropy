package io.nop.core.model.tree;

import io.nop.commons.collections.IterableIterator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.model.tree.impl.PostOrderDepthFirstIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPostOrderDepthFirstIterator {

    // 创建一个测试用的XML树结构
    private XNode createTestTree() {
        return XNode.parse("<root>\n" +
                "  <child1>\n" +
                "    <grandchild1/>\n" +
                "    <grandchild2>\n" +
                "      <greatgrandchild1/>\n" +
                "    </grandchild2>\n" +
                "  </child1>\n" +
                "  <child2>\n" +
                "    <grandchild3/>\n" +
                "  </child2>\n" +
                "  <child3/>\n" +
                "</root>");
    }

    // 创建一个简单的树结构适配器
    private final ITreeChildrenAdapter<XNode> xnodeAdapter = new ITreeChildrenAdapter<XNode>() {
        @Override
        public Collection<? extends XNode> getChildren(XNode node) {
            return node.getChildren();
        }
    };

    @Test
    public void testDepthLastTraversalOrder() {
        XNode root = createTestTree();

        List<String> nodeNames = new ArrayList<>();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, null);

        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 验证后序遍历顺序：先子节点，后父节点
        List<String> expectedOrder = List.of(
                "grandchild1", "greatgrandchild1", "grandchild2",
                "child1", "grandchild3", "child2", "child3", "root"
        );

        assertEquals(expectedOrder, nodeNames);
    }

    @Test
    public void testDepthLastWithoutRoot() {
        XNode root = createTestTree();

        List<String> nodeNames = new ArrayList<>();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, false, null);

        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 不包含root节点
        List<String> expectedOrder = List.of(
                "grandchild1", "greatgrandchild1", "grandchild2",
                "child1", "grandchild3", "child2", "child3"
        );

        assertEquals(expectedOrder, nodeNames);
    }

    @Test
    public void testWithFilter() {
        XNode root = createTestTree();

        // 过滤掉名为"child2"的节点及其子节点
        Predicate<XNode> filter = node -> !"child2".equals(node.getTagName());

        List<String> nodeNames = new ArrayList<>();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, filter);

        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 应该过滤掉child2及其子节点grandchild3
        List<String> expectedOrder = List.of(
                "grandchild1", "greatgrandchild1", "grandchild2",
                "child1", "grandchild3", "child3", "root"
        );

        assertEquals(expectedOrder, nodeNames);
    }

    @Test
    public void testWithSubtreeFilter() {
        XNode root = createTestTree();

        // 过滤掉名为"child2"的整个子树
        Predicate<XNode> filter = node -> !"child2".equals(node.getTagName());

        List<String> nodeNames = new ArrayList<>();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, filter, true);

        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 使用子树过滤模式，child2及其所有后代都不会出现
        List<String> expectedOrder = List.of(
                "grandchild1", "greatgrandchild1", "grandchild2",
                "child1", "child3", "root"
        );

        assertEquals(expectedOrder, nodeNames);
    }

    @Test
    public void testEmptyTree() {
        XNode root = XNode.parse("<root/>");

        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, null);
        List<XNode> nodes = new ArrayList<>();
        iterator.forEachRemaining(nodes::add);

        assertEquals(1, nodes.size());
        assertEquals("root", nodes.get(0).getTagName());
    }

    @Test
    public void testSingleNodeTree() {
        XNode root = XNode.parse("<single/>");

        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, null);
        List<XNode> nodes = new ArrayList<>();
        iterator.forEachRemaining(nodes::add);

        assertEquals(1, nodes.size());
        assertEquals("single", nodes.get(0).getTagName());
    }

    @Test
    public void testTreeVisitorsIntegration() {
        XNode root = createTestTree();

        // 测试通过TreeVisitors创建的迭代器
        IterableIterator<XNode> iterator = TreeVisitors.postOrderDepthFirstIterator(
                xnodeAdapter, root, true, null, false
        );

        List<String> nodeNames = new ArrayList<>();
        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 验证顺序正确
        assertTrue(nodeNames.indexOf("greatgrandchild1") < nodeNames.indexOf("grandchild2"));
        assertTrue(nodeNames.indexOf("grandchild2") < nodeNames.indexOf("child1"));
        assertTrue(nodeNames.indexOf("child1") < nodeNames.indexOf("root"));
    }

    @Test
    public void testTreeVisitorsWithSubtreeFilter() {
        XNode root = createTestTree();

        Predicate<XNode> filter = node -> !"child1".equals(node.getTagName());

        // 测试带子树过滤的TreeVisitors方法
        IterableIterator<XNode> iterator = TreeVisitors.postOrderDepthFirstIterator(
                xnodeAdapter, root, true, filter, true
        );

        List<String> nodeNames = new ArrayList<>();
        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // child1及其所有后代都应该被过滤掉
        assertFalse(nodeNames.contains("child1"));
        assertFalse(nodeNames.contains("grandchild1"));
        assertFalse(nodeNames.contains("grandchild2"));
        assertFalse(nodeNames.contains("greatgrandchild1"));

        // 其他节点应该存在
        assertTrue(nodeNames.contains("child2"));
        assertTrue(nodeNames.contains("grandchild3"));
        assertTrue(nodeNames.contains("child3"));
        assertTrue(nodeNames.contains("root"));
    }

    @Test
    public void testComplexTreeStructure() {
        // 创建一个更复杂的树结构
        XNode complexTree = XNode.parse("<a>\n" +
                "  <b>\n" +
                "    <c>\n" +
                "      <d/>\n" +
                "      <e>\n" +
                "        <f/>\n" +
                "      </e>\n" +
                "    </c>\n" +
                "    <g/>\n" +
                "  </b>\n" +
                "  <h>\n" +
                "    <i/>\n" +
                "  </h>\n" +
                "  <j/>\n" +
                "</a>");

        List<String> nodeNames = new ArrayList<>();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, complexTree, true, null);

        for (XNode node : iterator) {
            nodeNames.add(node.getTagName());
        }

        // 验证复杂的后序遍历顺序
        List<String> expectedOrder = List.of(
                "d", "f", "e", "c", "g", "b",
                "i", "h", "j", "a"
        );

        assertEquals(expectedOrder, nodeNames);
    }

    @Test
    public void testIteratorReuse() {
        XNode root = createTestTree();
        PostOrderDepthFirstIterator<XNode> iterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, null);

        // 第一次遍历
        List<String> firstPass = new ArrayList<>();
        iterator.forEachRemaining(node -> firstPass.add(node.getTagName()));

        // 第二次遍历应该为空
        assertFalse(iterator.hasNext());

        // 重新创建迭代器
        PostOrderDepthFirstIterator<XNode> newIterator = new PostOrderDepthFirstIterator<>(xnodeAdapter, root, true, null);
        List<String> secondPass = new ArrayList<>();
        newIterator.forEachRemaining(node -> secondPass.add(node.getTagName()));

        // 两次遍历结果应该相同
        assertEquals(firstPass, secondPass);
    }
}