package io.nop.markdown.simple;

import io.nop.commons.collections.MutableIntArray;
import io.nop.markdown.model.MarkdownSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MarkdownSectionTest {

    @Test
    void testNormalizeSectionNo_WithRootAndChildren() {
        // 构建测试结构：
        // # 根标题 (level=0，不编号)
        // ## 章节1 (level=1 → 编号1)
        // ### 子章节1.1 (level=2 → 编号1.1)
        // ### 子章节1.2 (level=2 → 编号1.2)
        // ## 章节2 (level=1 → 编号2)
        MarkdownSection root = new MarkdownSection(0, "根标题");
        MarkdownSection section1 = new MarkdownSection(1, "章节1");
        MarkdownSection section1_1 = new MarkdownSection(2, "子章节1.1");
        MarkdownSection section1_2 = new MarkdownSection(2, "子章节1.2");
        MarkdownSection section2 = new MarkdownSection(1, "章节2");

        root.addChild(section1);
        section1.addChild(section1_1);
        section1.addChild(section1_2);
        root.addChild(section2);

        // 执行编号逻辑
        root.normalizeSectionNo(null);

        // 验证根节点（不编号）
        assertEquals("根标题", root.getTitle());

        // 验证直接子节点（level=1）
        assertEquals("1 章节1", section1.getTitleWithSectionNo());
        assertEquals("2 章节2", section2.getTitleWithSectionNo());

        // 验证更深层节点（level=2）
        assertEquals("1.1 子章节1.1", section1_1.getTitleWithSectionNo());
        assertEquals("1.2 子章节1.2", section1_2.getTitleWithSectionNo());
    }

    @Test
    void testNormalizeSectionNo_WithCustomParentNumbers() {
        // 测试从自定义父编号（如 [2, 1]）开始编号
        // 初始编号：2.1
        // 新增节点（level=2 → 编号2.1.1）
        MarkdownSection section = new MarkdownSection(2, "子章节");
        MutableIntArray parentNumbers = MutableIntArray.of(2, 1); // 初始编号 2.1

        section.normalizeSectionNo(parentNumbers);

        // 验证编号是否正确递增
        assertEquals("2.2 子章节", section.getTitleWithSectionNo());
    }

    @Test
    void testNormalizeSectionNo_WithEmptyTitle() {
        // 测试空标题的节点（不应抛出异常）
        MarkdownSection section = new MarkdownSection(1, null);
        section.normalizeSectionNo(null);

        assertNull(section.getTitle()); // 无变化
    }

    @Test
    void testNormalizeSectionNo_WithExistingSectionNo() {
        // 测试已存在编号的标题（应移除旧编号，重新生成）
        MarkdownSection section = new MarkdownSection(1, "3.2 旧标题");
        section.normalizeSectionNo(null);

        // 旧编号 "3.2" 被移除，新编号从 1 开始
        assertEquals("1 旧标题", section.getTitleWithSectionNo());
    }

    @Test
    void testToIndexMarkdown_WithChildren_Depth1() {
        // 测试带子节点，depth=1（生成同级文件链接）
        MarkdownSection root = new MarkdownSection(1, "根节点");
        MarkdownSection child1 = new MarkdownSection(2, "1.1 子节点1");
        MarkdownSection child2 = new MarkdownSection(2, "1.2 子节点2");
        root.addChild(child1);
        root.addChild(child2);

        String result = root.toIndexMarkdown(1, null);
        String expected = "# 根节点\n\n\n" +
                "## [1.1 子节点1](section-1.1.md)\n" +
                "## [1.2 子节点2](section-1.2.md)\n";
        assertEquals(expected, result);
    }
}