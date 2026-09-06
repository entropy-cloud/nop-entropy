package io.nop.markdown.table;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.model.table.impl.BaseCell;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTableViewToMarkdownTableConverter {

    @Test
    public void testRegularTableKeepsLastColumn() {
        // 规则表格：每行 cell 数 == 全表列数（最常见情形）
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "|----------|----------|\n" +
                        "| Cell 1   | Cell 2   |\n" +
                        "| Cell 3   | Cell 4   |";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);
        MarkdownTable mdTable = MarkdownTable.fromTableView(table);

        // 修复前：CollectionHelper.set(headers, cols-1, null) 把最后一个表头覆盖为 null
        assertEquals("Header 1", mdTable.getHeaders().get(0));
        assertEquals("Header 2", mdTable.getHeaders().get(1));

        // 数据行最后一列同样不被覆盖
        assertEquals("Cell 2", mdTable.getRows().get(0).get(1));
        assertEquals("Cell 4", mdTable.getRows().get(1).get(1));

        // 渲染结果包含最后一列的内容
        String text = mdTable.toMarkdown();
        assertTrue(text.contains("Header 2"));
        assertTrue(text.contains("Cell 4"));
    }

    @Test
    public void testSparseRowPaddedToTableColumns() {
        // 稀疏表：header 行只有 1 个 cell，数据行有 2 个 cell → 全表列数为 2
        BaseTable table = new BaseTable();
        BaseRow headerRow = new BaseRow();
        headerRow.internalAddCell(cellOf("Header 1"));
        table.addRow(headerRow);

        BaseRow dataRow = new BaseRow();
        dataRow.internalAddCell(cellOf("Cell 1"));
        dataRow.internalAddCell(cellOf("Cell 2"));
        table.addRow(dataRow);

        assertEquals(2, table.getColCount());

        MarkdownTable mdTable = MarkdownTable.fromTableView(table);

        // 表头补齐到全表列数，已有表头不被覆盖，缺失列以 null 占位
        assertEquals(2, mdTable.getHeaders().size());
        assertEquals("Header 1", mdTable.getHeaders().get(0));
        assertNull(mdTable.getHeaders().get(1));
        // 数据行完整保留
        assertEquals("Cell 1", mdTable.getRows().get(0).get(0));
        assertEquals("Cell 2", mdTable.getRows().get(0).get(1));
    }

    private static BaseCell cellOf(String text) {
        BaseCell cell = new BaseCell();
        cell.setValue(text);
        return cell;
    }
}
