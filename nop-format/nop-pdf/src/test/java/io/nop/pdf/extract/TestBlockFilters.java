package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表格过滤器语义：minCol/maxCol 对列数构成闭开区间约束（0 表示该侧不限制）
 */
public class TestBlockFilters {

    private static TableBlock tableWithCols(int colCount) {
        TableBlock table = new TableBlock();
        table.setViewBounding(new Rectangle2D.Double(0, 0, 100, 20));
        TableCellBlock cell = new TableCellBlock(0, 0, 1, colCount);
        cell.setContent("h");
        table.addCell(0, 0, cell);
        return table;
    }

    @Test
    public void testMinColRejectsNarrowTables() {
        Predicate<TableBlock> filter = BlockFilters.filterTable(3, 0);
        assertFalse(filter.test(tableWithCols(2)));
        assertTrue(filter.test(tableWithCols(3)));
        assertTrue(filter.test(tableWithCols(5)));
    }

    @Test
    public void testMaxColRejectsWideTables() {
        Predicate<TableBlock> filter = BlockFilters.filterTable(0, 2);
        assertTrue(filter.test(tableWithCols(2)));
        assertFalse(filter.test(tableWithCols(3)));
    }

    @Test
    public void testRangeFilterIsInclusive() {
        Predicate<TableBlock> filter = BlockFilters.filterTable(2, 3);
        assertFalse(filter.test(tableWithCols(1)));
        assertTrue(filter.test(tableWithCols(2)));
        assertTrue(filter.test(tableWithCols(3)));
        assertFalse(filter.test(tableWithCols(4)));
    }

    @Test
    public void testZeroMeansUnbounded() {
        Predicate<TableBlock> noLimit = BlockFilters.filterTable(0, 0);
        assertTrue(noLimit.test(tableWithCols(1)));
        assertTrue(noLimit.test(tableWithCols(9)));
    }
}
