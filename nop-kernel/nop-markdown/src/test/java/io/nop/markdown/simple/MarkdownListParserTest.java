package io.nop.markdown.simple;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.markdown.model.MarkdownListItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Markdown列表解析器测试")
class MarkdownListParserTest {

    private MarkdownListParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownListParser();
    }

    @Test
    void testTwoLevelNesting() {
        String text = "- Level 0 Item 1\n" +
                "  - Level 1 Item 1\n" +
                "  - Level 1 Item 2\n" +
                "- Level 0 Item 2\n";

        parser.setSupportNested(true);
        List<MarkdownListItem> items = parser.parseAllListItems(null, text);

        assertEquals(2, items.size()); // ✅ 应该有2个顶层项

        MarkdownListItem firstItem = items.get(0);
        assertEquals(2, firstItem.getChildCount()); // ✅ 第一项有2个子项
    }

    @Nested
    @DisplayName("基础解析测试")
    class BasicParsingTests {

        @Test
        @DisplayName("解析简单无序列表")
        void testSimpleUnorderedList() {
            String text = "- Item 1\n" +
                          "- Item 2\n" +
                          "- Item 3\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(3, items.size(), "应该解析出3个列表项");
            assertEquals("Item 1", items.get(0).getContent());
            assertEquals("Item 2", items.get(1).getContent());
            assertEquals("Item 3", items.get(2).getContent());
            
            assertFalse(items.get(0).isOrdered(), "应该是无序列表");
            assertFalse(items.get(1).isOrdered(), "应该是无序列表");
            assertFalse(items.get(2).isOrdered(), "应该是无序列表");
        }

        @Test
        @DisplayName("解析简单有序列表")
        void testSimpleOrderedList() {
            String text = "1. First item\n" +
                          "2. Second item\n" +
                          "3. Third item\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(3, items.size(), "应该解析出3个列表项");
            assertEquals("First item", items.get(0).getContent());
            assertEquals("Second item", items.get(1).getContent());
            assertEquals("Third item", items.get(2).getContent());
            
            assertTrue(items.get(0).isOrdered(), "应该是有序列表");
            assertTrue(items.get(1).isOrdered(), "应该是有序列表");
            assertTrue(items.get(2).isOrdered(), "应该是有序列表");
            
            assertEquals(1, items.get(0).getItemIndex());
            assertEquals(2, items.get(1).getItemIndex());
            assertEquals(3, items.get(2).getItemIndex());
        }

        @Test
        @DisplayName("支持不同的无序列表标记")
        void testDifferentUnorderedMarkers() {
            String text = "- Dash\n" +
                          "* Asterisk\n" +
                          "+ Plus\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(3, items.size());
            assertEquals("Dash", items.get(0).getContent());
            assertEquals("Asterisk", items.get(1).getContent());
            assertEquals("Plus", items.get(2).getContent());
        }

        @Test
        @DisplayName("解析多行内容")
        void testMultilineContent() {
            String text = "- Line 1\n" +
                          "  Line 2\n" +
                          "  Line 3\n" +
                          "- Next item\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size());
            String firstContent = items.get(0).getContent();
            assertTrue(firstContent.contains("Line 1"), "应该包含第一行");
            assertTrue(firstContent.contains("Line 2"), "应该包含第二行");
            assertTrue(firstContent.contains("Line 3"), "应该包含第三行");
        }
    }

    @Nested
    @DisplayName("嵌套列表测试")
    class NestedListTests {

        @Test
        @DisplayName("解析两层嵌套列表")
        void testTwoLevelNesting() {
            String text = "- Level 0 Item 1\n" +
                          "  - Level 1 Item 1\n" +
                          "  - Level 1 Item 2\n" +
                          "- Level 0 Item 2\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size(), "应该有2个顶层项");
            
            MarkdownListItem firstItem = items.get(0);
            assertEquals("Level 0 Item 1", firstItem.getContent());
            assertEquals(0, firstItem.getListLevel(), "应该是0层");
            assertTrue(firstItem.hasChildren(), "应该有子项");
            assertEquals(2, firstItem.getChildCount(), "应该有2个子项");
            
            MarkdownListItem firstChild = firstItem.getChild(0);
            assertEquals("Level 1 Item 1", firstChild.getContent());
            assertEquals(1, firstChild.getListLevel(), "应该是1层");
            
            MarkdownListItem secondChild = firstItem.getChild(1);
            assertEquals("Level 1 Item 2", secondChild.getContent());
            assertEquals(1, secondChild.getListLevel(), "应该是1层");
            
            MarkdownListItem secondItem = items.get(1);
            assertEquals("Level 0 Item 2", secondItem.getContent());
            assertFalse(secondItem.hasChildren(), "不应该有子项");
        }

        @Test
        @DisplayName("解析三层嵌套列表")
        void testThreeLevelNesting() {
            String text = "- Level 0\n" +
                          "  - Level 1\n" +
                          "    - Level 2\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(1, items.size(), "应该有1个顶层项");
            
            MarkdownListItem level0 = items.get(0);
            assertEquals(0, level0.getListLevel());
            assertTrue(level0.hasChildren());
            
            MarkdownListItem level1 = level0.getChild(0);
            assertEquals(1, level1.getListLevel());
            assertTrue(level1.hasChildren());
            
            MarkdownListItem level2 = level1.getChild(0);
            assertEquals(2, level2.getListLevel());
            assertFalse(level2.hasChildren());
        }

        @Test
        @DisplayName("混合有序和无序列表")
        void testMixedOrderedAndUnordered() {
            String text = "- Unordered parent\n" +
                          "  1. Ordered child 1\n" +
                          "  2. Ordered child 2\n" +
                          "- Another unordered\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size());
            
            MarkdownListItem first = items.get(0);
            assertFalse(first.isOrdered(), "父项应该是无序的");
            assertEquals(2, first.getChildCount());
            
            MarkdownListItem child1 = first.getChild(0);
            assertTrue(child1.isOrdered(), "子项应该是有序的");
            assertEquals(1, child1.getItemIndex());
            
            MarkdownListItem child2 = first.getChild(1);
            assertTrue(child2.isOrdered(), "子项应该是有序的");
            assertEquals(2, child2.getItemIndex());
        }

        @Test
        @DisplayName("测试列表层级计数")
        void testListLevelCounting() {
            String text = "1. First\n" +
                          "  1. Sub first\n" +
                          "  2. Sub second\n" +
                          "2. Second\n" +
                          "  1. Sub third\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size());
            
            // 检查第一个顶层项
            assertEquals(1, items.get(0).getItemIndex());
            assertEquals(1, items.get(0).getChild(0).getItemIndex());
            assertEquals(2, items.get(0).getChild(1).getItemIndex());
            
            // 检查第二个顶层项
            assertEquals(2, items.get(1).getItemIndex());
            assertEquals(1, items.get(1).getChild(0).getItemIndex());
        }
    }

    @Nested
    @DisplayName("Tab和空格处理测试")
    class TabAndSpaceTests {

        @Test
        @DisplayName("Tab字符应该被展开为空格")
        void testTabExpansion() {
            String text = "-\tItem with tab\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(1, items.size());
            String content = items.get(0).getContent();
            assertFalse(content.contains("\t"), "内容不应该包含Tab字符");
            assertTrue(content.contains("Item with tab"), "应该包含原始文本");
        }

        @Test
        @DisplayName("混合使用Tab和空格缩进")
        void testMixedTabAndSpaceIndentation() {
            String text = "- Item 1\n" +
                          "\t- Tab indented\n" +
                          "    - Four space indented\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(1, items.size(), "应该有1个顶层项");
            MarkdownListItem root = items.get(0);
            assertEquals(2, root.getChildCount(), "应该有2个子项");
            
            // 两个子项都应该被正确识别
            assertNotNull(root.getChild(0));
            assertNotNull(root.getChild(1));
        }

        @Test
        @DisplayName("计算正确的缩进宽度")
        void testIndentWidthCalculation() {
            String text = "- Root\n" +
                          "  - Two spaces\n" +
                          "    - Four spaces\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            MarkdownListItem root = items.get(0);
            assertEquals(0, root.getRawIndent(), "顶层应该是0缩进");
            
            MarkdownListItem level1 = root.getChild(0);
            assertEquals(2, level1.getRawIndent(), "第一层应该是2缩进");
            
            MarkdownListItem level2 = level1.getChild(0);
            assertEquals(4, level2.getRawIndent(), "第二层应该是4缩进");
        }
    }

    @Nested
    @DisplayName("模式切换测试")
    class ModeSwitchTests {

        @Test
        @DisplayName("简单模式不应该构建嵌套结构")
        void testSimpleModeFlattensStructure() {
            String text = "- Item 1\n" +
                    "  - Sub item\n" +
                    "- Item 2\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            // ✅ 修正：简单模式应该解析出3个扁平项
            assertEquals(3, items.size(), "简单模式应该解析为3个扁平项");
            assertFalse(items.get(0).hasChildren(), "简单模式不应该有子项");
            assertFalse(items.get(1).hasChildren(), "简单模式不应该有子项");
            assertFalse(items.get(2).hasChildren(), "简单模式不应该有子项");

            // ✅ 验证内容
            assertEquals("Item 1", items.get(0).getContent());
            assertEquals("Sub item", items.get(1).getContent());
            assertEquals("Item 2", items.get(2).getContent());

            // ✅ 验证都是0层级
            assertEquals(0, items.get(0).getListLevel());
            assertEquals(0, items.get(1).getListLevel());
            assertEquals(0, items.get(2).getListLevel());
        }

        @Test
        @DisplayName("嵌套模式应该构建树形结构")
        void testNestedModeBuildsTree() {
            String text = "- Item 1\n" +
                          "  - Sub item\n" +
                          "- Item 2\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size(), "应该有2个顶层项");
            assertTrue(items.get(0).hasChildren(), "第一项应该有子项");
            assertEquals(1, items.get(0).getChildCount(), "第一项应该有1个子项");
        }

        @Test
        @DisplayName("动态切换模式")
        void testDynamicModeSwitch() {
            String text = "- Item 1\n" +
                          "  - Sub item\n";

            // 先用简单模式
            parser.setSupportNested(false);
            List<MarkdownListItem> flatItems = parser.parseAllListItems(null, text);
            assertEquals(2, flatItems.size());

            // 切换到嵌套模式
            parser.setSupportNested(true);
            List<MarkdownListItem> nestedItems = parser.parseAllListItems(null, text);
            assertEquals(1, nestedItems.size());
            assertTrue(nestedItems.get(0).hasChildren());
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空字符串应该返回空列表")
        void testEmptyString() {
            List<MarkdownListItem> items = parser.parseAllListItems(null, "");
            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("只有空行应该返回空列表")
        void testOnlyEmptyLines() {
            List<MarkdownListItem> items = parser.parseAllListItems(null, "\n\n\n");
            assertNotNull(items);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("单个列表项")
        void testSingleItem() {
            List<MarkdownListItem> items = parser.parseAllListItems(null, "- Single item");
            assertEquals(1, items.size());
            assertEquals("Single item", items.get(0).getContent());
        }

        @Test
        @DisplayName("标记后没有空格不是有效列表")
        void testMarkerWithoutSpace() {
            List<MarkdownListItem> items = parser.parseAllListItems(null, "-NoSpace");
            assertTrue(items.isEmpty(), "没有空格的标记不应该被识别为列表");
        }

        @Test
        @DisplayName("空内容的列表项")
        void testEmptyContent() {
            String text = "- \n" +
                          "- Item 2\n";
            
            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);
            assertEquals(2, items.size());
            assertTrue(items.get(0).getContent().isEmpty() || 
                      items.get(0).getContent().trim().isEmpty());
            assertEquals("Item 2", items.get(1).getContent());
        }

        @Test
        @DisplayName("列表项之间有空行")
        void testEmptyLinesBetweenItems() {
            String text = "- Item 1\n" +
                          "\n" +
                          "- Item 2\n";
            
            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);
            assertEquals(2, items.size());
        }

        @Test
        @DisplayName("大数字的有序列表")
        void testLargeOrderNumbers() {
            String text = "999. Item 999\n" +
                          "1000. Item 1000\n";
            
            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);
            assertEquals(2, items.size());
            assertTrue(items.get(0).isOrdered());
            assertTrue(items.get(1).isOrdered());
        }
    }

    @Nested
    @DisplayName("位置信息测试")
    class LocationTests {

        @Test
        @DisplayName("记录正确的起始和结束位置")
        void testPositionTracking() {
            String text = "- Item 1\n" +
                          "- Item 2\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            MarkdownListItem first = items.get(0);
            assertEquals(0, first.getStartPos(), "第一项应该从位置0开始");
            assertTrue(first.getEndPos() > first.getStartPos(), "结束位置应该大于起始位置");

            MarkdownListItem second = items.get(1);
            assertEquals(first.getEndPos(), second.getStartPos(), 
                        "第二项应该从第一项结束位置开始");
        }

        @Test
        @DisplayName("设置源码位置信息")
        void testSourceLocationSetting() {
            SourceLocation baseLocation = SourceLocation.fromPath("test.md");
            String text = "- Item 1\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(baseLocation, text);

            assertNotNull(items.get(0).getLocation(), "应该设置位置信息");
            assertTrue(items.get(0).getLocation().getPath().endsWith("test.md"));
        }

        @Test
        @DisplayName("null位置信息不应该导致错误")
        void testNullLocationHandling() {
            String text = "- Item 1\n";
            
            assertDoesNotThrow(() -> {
                List<MarkdownListItem> items = parser.parseAllListItems(null, text);
                assertNull(items.get(0).getLocation());
            });
        }
    }

    @Nested
    @DisplayName("冻结和不可变性测试")
    class FreezeTests {

        @Test
        @DisplayName("冻结后不能修改")
        void testCannotModifyFrozenItem() {
            String text = "- Item 1\n";
            
            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItemsImmutable(null, text);
            MarkdownListItem item = items.get(0);

            assertTrue(item.isFrozen(), "应该已经冻结");
            
            assertThrows(NopException.class, () -> {
                item.setContent("Modified");
            }, "修改冻结的项应该抛出异常");
        }

        @Test
        @DisplayName("冻结应该递归应用到子项")
        void testFreezeRecursive() {
            String text = "- Parent\n" +
                          "  - Child\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItemsImmutable(null, text);

            MarkdownListItem parent = items.get(0);
            MarkdownListItem child = parent.getChild(0);

            assertTrue(parent.isFrozen(), "父项应该冻结");
            assertTrue(child.isFrozen(), "子项也应该冻结");
        }

        @Test
        @DisplayName("未冻结的项可以修改")
        void testCanModifyUnfrozenItem() {
            String text = "- Item 1\n";
            
            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);
            MarkdownListItem item = items.get(0);

            assertFalse(item.isFrozen(), "不应该冻结");
            
            assertDoesNotThrow(() -> {
                item.setContent("Modified");
            });
            
            assertEquals("Modified", item.getContent());
        }
    }

    @Nested
    @DisplayName("forEach遍历测试")
    class ForEachTests {

        @Test
        @DisplayName("简单模式遍历所有项")
        void testForEachSimpleMode() {
            String text = "- Item 1\n" +
                          "- Item 2\n" +
                          "- Item 3\n";

            parser.setSupportNested(false);
            
            final int[] count = {0};
            parser.forEachListItem(null, text, item -> {
                count[0]++;
            });

            assertEquals(3, count[0], "应该遍历3个项");
        }

        @Test
        @DisplayName("嵌套模式深度优先遍历")
        void testForEachNestedMode() {
            String text = "- Parent 1\n" +
                          "  - Child 1\n" +
                          "  - Child 2\n" +
                          "- Parent 2\n";

            parser.setSupportNested(true);
            
            final int[] count = {0};
            final StringBuilder order = new StringBuilder();
            
            parser.forEachListItem(null, text, item -> {
                count[0]++;
                order.append(item.getContent()).append(";");
            });

            assertEquals(4, count[0], "应该遍历4个项");
            assertEquals("Parent 1;Child 1;Child 2;Parent 2;", order.toString(),
                        "应该按深度优先顺序遍历");
        }
    }

    @Nested
    @DisplayName("文本重建测试")
    class TextRebuildTests {

        @Test
        @DisplayName("toText应该生成有效的Markdown")
        void testToTextGeneratesValidMarkdown() {
            String text = "- Item 1\n" +
                          "  - Sub item\n" +
                          "- Item 2\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            for (MarkdownListItem item : items) {
                String rebuilt = item.toText();
                assertNotNull(rebuilt);
                assertFalse(rebuilt.isEmpty());
                assertTrue(rebuilt.contains(item.getContent()));
            }
        }

        @Test
        @DisplayName("有序列表toText应该包含序号")
        void testOrderedListToText() {
            String text = "1. First\n" +
                          "2. Second\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            String firstText = items.get(0).toText();
            assertTrue(firstText.matches(".*\\d+\\..*"), "应该包含序号和点号");
        }
    }

    @Nested
    @DisplayName("异常处理测试")
    class ExceptionTests {

        @Test
        @DisplayName("null文本应该返回空列表或不抛异常")
        void testNullText() {
            // parseNextListItem 在 null 文本时应该返回 null
            MarkdownListItem item = parser.parseNextListItem(null, null, 0);
            assertNull(item, "null文本应该返回null");
        }

        @Test
        @DisplayName("无效的起始位置")
        void testInvalidStartPosition() {
            String text = "- Item 1\n";
            
            // 负数位置
            MarkdownListItem item = parser.parseNextListItem(null, text, -1);
            assertNull(item, "负数位置应该返回null");
            
            // 超出范围的位置
            item = parser.parseNextListItem(null, text, 1000);
            assertNull(item, "超出范围应该返回null");
        }
    }

    @Nested
    @DisplayName("性能和复杂场景测试")
    class ComplexScenarioTests {

        @Test
        @DisplayName("深层嵌套列表")
        void testDeepNesting() {
            StringBuilder text = new StringBuilder();
            int depth = 5;
            
            for (int i = 0; i < depth; i++) {
                text.append("  ".repeat(i)).append("- Level ").append(i).append("\n");
            }

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text.toString());

            assertEquals(1, items.size(), "应该有1个顶层项");
            
            MarkdownListItem current = items.get(0);
            for (int i = 1; i < depth; i++) {
                assertTrue(current.hasChildren(), "Level " + (i-1) + " 应该有子项");
                current = current.getChild(0);
                assertEquals(i, current.getListLevel(), "应该是正确的层级");
            }
        }

        @Test
        @DisplayName("大量列表项")
        void testManyItems() {
            StringBuilder text = new StringBuilder();
            int itemCount = 100;
            
            for (int i = 1; i <= itemCount; i++) {
                text.append("- Item ").append(i).append("\n");
            }

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text.toString());

            assertEquals(itemCount, items.size(), "应该解析所有项");
            assertEquals("Item 1", items.get(0).getContent());
            assertEquals("Item " + itemCount, items.get(itemCount - 1).getContent());
        }

        @Test
        @DisplayName("复杂的混合场景")
        void testComplexMixedScenario() {
            // 使用标准的2空格缩进
            String text = "1. Ordered parent\n" +
                          "  - Unordered child 1\n" +
                          "    1. Ordered grandchild\n" +
                          "  - Unordered child 2\n" +
                          "2. Second ordered parent\n" +
                          "  * Different marker\n" +
                          "  + Another marker\n";

            parser.setSupportNested(true);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(2, items.size(), "应该有2个顶层项，实际: " + items.size());
            
            MarkdownListItem first = items.get(0);
            assertTrue(first.isOrdered(), "第一项应该是有序的");
            assertEquals(2, first.getChildCount(), "第一项应该有2个子项");
            
            MarkdownListItem firstChild = first.getChild(0);
            assertFalse(firstChild.isOrdered(), "第一个子项应该是无序的");
            assertTrue(firstChild.hasChildren(), "第一个子项应该有孙子项");
            
            MarkdownListItem grandchild = firstChild.getChild(0);
            assertTrue(grandchild.isOrdered(), "孙子项应该是有序的");
            assertEquals(2, grandchild.getListLevel(), "孙子项应该是第2层");
            
            MarkdownListItem second = items.get(1);
            assertTrue(second.isOrdered(), "第二项应该是有序的");
            assertEquals(2, second.getChildCount(), "第二项应该有2个子项");
        }
    }

    @Nested
    @DisplayName("特殊格式测试")
    class SpecialFormatTests {

        @Test
        @DisplayName("列表项包含特殊字符")
        void testSpecialCharacters() {
            String text = "- Item with **bold**\n" +
                          "- Item with `code`\n" +
                          "- Item with [link](url)\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(3, items.size());
            assertTrue(items.get(0).getContent().contains("**bold**"));
            assertTrue(items.get(1).getContent().contains("`code`"));
            assertTrue(items.get(2).getContent().contains("[link](url)"));
        }

        @Test
        @DisplayName("连续的有序列表数字可以不连续")
        void testNonConsecutiveNumbers() {
            String text = "1. First\n" +
                          "5. Fifth\n" +
                          "10. Tenth\n";

            parser.setSupportNested(false);
            List<MarkdownListItem> items = parser.parseAllListItems(null, text);

            assertEquals(3, items.size());
            // 解析器应该能识别，但index是重新计数的
            assertTrue(items.get(0).isOrdered());
            assertTrue(items.get(1).isOrdered());
            assertTrue(items.get(2).isOrdered());
        }
    }
}