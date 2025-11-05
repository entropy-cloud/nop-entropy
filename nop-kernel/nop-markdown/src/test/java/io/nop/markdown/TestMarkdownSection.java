package io.nop.markdown;

import io.nop.commons.util.StringHelper;
import io.nop.markdown.model.MarkdownSection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMarkdownSection {

    @Test
    public void testBuildTree() {
        // 构造测试数据
        MarkdownSection h1_1 = new MarkdownSection(2, "h1");
        MarkdownSection h2_1 = new MarkdownSection(3, "h2");
        MarkdownSection h3_1 = new MarkdownSection(4, "h3");
        MarkdownSection h2_2 = new MarkdownSection(3, "h2");
        MarkdownSection h1_2 = new MarkdownSection(1, "h1");
        MarkdownSection h2_3 = new MarkdownSection(2, "h2");

        List<MarkdownSection> blocks = List.of(h1_1, h2_1, h3_1, h2_2, h1_2, h2_3);

        // 构建树
        List<MarkdownSection> tree = MarkdownSection.buildTree(blocks);
        System.out.println(StringHelper.join(tree, "\n"));

        // 验证树结构
        assertEquals(2, tree.size()); // 根节点下应该有两个 h1
        assertEquals(h1_1, tree.get(0));
        assertEquals(h1_2, tree.get(1));

        // 验证 h1_1 的子节点
        List<MarkdownSection> h1_1_children = h1_1.getChildren();
        assertEquals(2, h1_1_children.size());
        assertEquals(h2_1, h1_1_children.get(0));
        assertEquals(h2_2, h1_1_children.get(1));

        // 验证 h2_1 的子节点（h3_1）
        List<MarkdownSection> h2_1_children = h2_1.getChildren();
        assertEquals(1, h2_1_children.size());
        assertEquals(h3_1, h2_1_children.get(0));

        // 验证 h1_2 的子节点（h2_3）
        List<MarkdownSection> h1_2_children = h1_2.getChildren();
        assertEquals(1, h1_2_children.size());
        assertEquals(h2_3, h1_2_children.get(0));
    }
}