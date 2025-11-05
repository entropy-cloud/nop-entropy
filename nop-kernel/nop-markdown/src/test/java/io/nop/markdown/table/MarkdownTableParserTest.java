package io.nop.markdown.table;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MarkdownTableParserTest {

    @Test
    public void testParseSimpleTable() {
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "|----------|----------|\n" +
                        "| Cell 1   | Cell 2   |\n" +
                        "| Cell 3   | Cell 4   |";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);

        assertEquals(3, table.getRowCount());

        // Check header row
        BaseRow headerRow = table.getRow(0);
        assertEquals(2, headerRow.getCellCount());
        assertEquals("Header 1", getCellValue(headerRow, 0));
        assertEquals("Header 2", getCellValue(headerRow, 1));

        // Check first data row
        BaseRow dataRow1 = table.getRow(1);
        assertEquals(2, dataRow1.getCellCount());
        assertEquals("Cell 1", getCellValue(dataRow1, 0));
        assertEquals("Cell 2", getCellValue(dataRow1, 1));

        // Check second data row
        BaseRow dataRow2 = table.getRow(2);
        assertEquals(2, dataRow2.getCellCount());
        assertEquals("Cell 3", getCellValue(dataRow2, 0));
        assertEquals("Cell 4", getCellValue(dataRow2, 1));
    }

    @Test
    public void testParseTableWithEmptyCells() {
        String markdown =
                "| Header 1 | Header 2 | Header 3 |\n" +
                        "|----------|----------|----------|\n" +
                        "| Cell 1   |          | Cell 3   |\n" +
                        "|          | Cell 5   |          |";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);

        assertEquals(3, table.getRowCount());

        BaseRow dataRow1 = table.getRow(1);
        assertEquals(3, dataRow1.getCellCount());
        assertEquals("Cell 1", getCellValue(dataRow1, 0));
        assertEquals("", getCellValue(dataRow1, 1));
        assertEquals("Cell 3", getCellValue(dataRow1, 2));

        BaseRow dataRow2 = table.getRow(2);
        assertEquals(3, dataRow2.getCellCount());
        assertEquals("", getCellValue(dataRow2, 0));
        assertEquals("Cell 5", getCellValue(dataRow2, 1));
        assertEquals("", getCellValue(dataRow2, 2));
    }

    @Test
    public void testParseTableWithEscapedCharacters() {
        String markdown =
                "| Header \\| 1 | Header \\\\ 2 |\n" +
                        "|-------------|-------------|\n" +
                        "| Cell \\| 1   | Cell \\\\ 2   |";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);

        assertEquals(2, table.getRowCount());

        BaseRow headerRow = table.getRow(0);
        assertEquals("Header | 1", getCellValue(headerRow, 0));
        assertEquals("Header \\ 2", getCellValue(headerRow, 1));

        BaseRow dataRow = table.getRow(1);
        assertEquals("Cell | 1", getCellValue(dataRow, 0));
        assertEquals("Cell \\ 2", getCellValue(dataRow, 1));
    }

    @Test
    public void testInvalidSeparatorRow() {
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "|----------|invalid--|\n" +
                        "| Cell 1   | Cell 2   |";

        assertThrows(RuntimeException.class, () -> {
            MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);
        });
    }

    @Test
    public void testMissingSeparatorRow() {
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "| Cell 1   | Cell 2   |";

        assertThrows(RuntimeException.class, () -> {
            MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);
        });
    }

    @Test
    public void testParseTableWithTextScanner() {
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "|----------|----------|\n" +
                        "| Cell 1   | Cell 2   |";

        TextScanner sc = TextScanner.fromString(SourceLocation.UNKNOWN, markdown);
        BaseTable table = MarkdownTableParser.parseTable(sc);

        assertEquals(2, table.getRowCount());
        assertEquals(2, table.getRow(0).getCellCount());
        assertEquals("Header 1", getCellValue(table.getRow(0), 0));
    }

    @Test
    public void testParseTableWithTrailingSpaces() {
        String markdown =
                "| Header 1   | Header 2   |\n" +
                        "|------------|------------|\n" +
                        "|   Cell 1   |   Cell 2   |";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);

        assertEquals(2, table.getRowCount());
        assertEquals("Header 1", getCellValue(table.getRow(0), 0));
        assertEquals("Cell 1", getCellValue(table.getRow(1), 0));
    }

    @Test
    public void testParseTableWithMultipleLines() {
        String markdown =
                "| Header 1 | Header 2 |\n" +
                        "|----------|----------|\n" +
                        "| Cell 1   | Cell 2   |\n" +
                        "\n" +
                        "| Cell 3   | Cell 4   |\n" +
                        "\n" +
                        "Some text after table";

        BaseTable table = MarkdownTableParser.parseTable(SourceLocation.UNKNOWN, markdown);

        assertEquals(3, table.getRowCount());
        assertEquals(2, table.getRow(2).getCellCount());
        assertEquals("Cell 3", getCellValue(table.getRow(2), 0));
    }

    private String getCellValue(BaseRow row, int col) {
        ICellView cell = row.getCell(col);
        return cell != null ? cell.getValue().toString() : "";
    }
}