package io.nop.markdown.utils;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.markdown.table.MarkdownTableParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestMarkdownTableHelper {

    @Test
    public void testParseMappingTableUsesRowDataAsKey() {
        String text = "| source | target |\n" +
                "|----|----|\n" +
                "| a | 1 |\n" +
                "| b | 2 |\n" +
                "| c | 3 |";

        Map<String, String> map = MarkdownTableHelper.parseMappingTable(SourceLocation.UNKNOWN, text);

        // 修复前：key 恒为表头名且逐行覆盖，结果只有 {source:b, target:2}
        assertEquals(3, map.size());
        assertEquals("1", map.get("a"));
        assertEquals("2", map.get("b"));
        assertEquals("3", map.get("c"));
    }

    /**
     * 尾部空单元格语义的特征化测试（与 GFM 对齐，供后续审计对照）：
     * 由 | 终止的空单元格是真实占位；行尾最后一个 | 之后的空段是虚拟单元格，丢弃。
     * 2026-08-22 审计条目曾断言 `| a | | |` 解析为 2 个 cell，实际为 3 个，该断言不成立
     */
    @Test
    public void testTrailingEmptyCellSemantics() {
        assertEquals(2, parseRowCells("| a | b |"));
        assertEquals(3, parseRowCells("| a | | |"));
        assertEquals(2, parseRowCells("| a | |"));
        assertEquals(2, parseRowCells("| a | b"));
        // 尾随空白段（无尾管道）视为空白单元格，按 GFM 规则丢弃
        assertEquals(1, parseRowCells("| a | "));
        assertEquals(1, parseRowCells("| a |"));
        assertEquals(3, parseRowCells("| a |  |  |"));
    }

    private int parseRowCells(String row) {
        String md = "| h1 | h2 | h3 |\n|---|---|---|\n" + row + "\n";
        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, md);
        BaseRow r = table.getRow(1);
        return r.getCellCount();
    }
}
