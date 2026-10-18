package io.nop.core.model.table.utils;

import io.nop.core.model.table.CellRange;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTableSplitHelperBreaks {

    /** 均匀行列的测试表（BaseTable + 行高显式设置 + 单元格占位使列数生效） */
    static BaseTable fixture(int rows, int cols, double rowHeight) {
        BaseTable table = new BaseTable();
        for (int r = 0; r < rows; r++) {
            BaseRow row = new BaseRow();
            row.setHeight(rowHeight);
            for (int c = 0; c < cols; c++)
                row.getCells().add(null);
            table.getRows().add(row);
        }
        return table;
    }

    @Test
    public void testNoBreaksDelegatesToOldBehavior() {
        List<CellRange> plain = TableSplitHelper.splitTable(fixture(40, 3, 20), 400, 300, 60, 20, true);
        List<CellRange> withEmpty = TableSplitHelper.splitTable(fixture(40, 3, 20), 400, 300, 60, 20, true,
                Collections.emptySet(), null);
        assertEquals(plain.size(), withEmpty.size());
        for (int i = 0; i < plain.size(); i++) {
            assertEquals(plain.get(i).getFirstRowIndex(), withEmpty.get(i).getFirstRowIndex());
            assertEquals(plain.get(i).getLastRowIndex(), withEmpty.get(i).getLastRowIndex());
        }
    }

    @Test
    public void testRowBreakForcesSplit() {
        // 40 行 x 20pt = 800pt，maxHeight 300pt 本来就 3 页；break 在 row 9 之后强制页 1 恰好止于 row 9
        BaseTable table = fixture(40, 3, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                new HashSet<>(Arrays.asList(9)), null);
        assertEquals(9, regions.get(0).getLastRowIndex(), "manual break must end page 1 at row 9");
        assertEquals(10, regions.get(1).getFirstRowIndex(), "page 2 must start at row 10");
    }

    @Test
    public void testRowBreakCreatesPageEvenWhenContentFits() {
        // 10 行 x 20pt = 200pt 单页即可容纳；break after row 4 强制 2 页
        BaseTable table = fixture(10, 3, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                new HashSet<>(Arrays.asList(4)), null);
        assertEquals(2, regions.size());
        assertEquals(4, regions.get(0).getLastRowIndex());
        assertEquals(5, regions.get(1).getFirstRowIndex());
        assertEquals(9, regions.get(1).getLastRowIndex());
    }

    @Test
    public void testColBreakForcesColumnSplit() {
        // 6 列 x 60pt = 360pt，maxWidth 400 本来 1 页；break after col 2 强制 2 页
        BaseTable table = fixture(5, 6, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                null, new HashSet<>(Arrays.asList(2)));
        assertEquals(2, regions.size());
        assertEquals(2, regions.get(0).getLastColIndex());
        assertEquals(3, regions.get(1).getFirstColIndex());
    }

    @Test
    public void testBreakOutOfBoundsIgnored() {
        BaseTable table = fixture(10, 3, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                new HashSet<>(Arrays.asList(100, -1)), new HashSet<>(Arrays.asList(50)));
        assertEquals(1, regions.size(), "out-of-range breaks must be ignored");
    }

    @Test
    public void testBreakAtLastRowNoop() {
        BaseTable table = fixture(10, 3, 20);
        List<CellRange> withBreak = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                new HashSet<>(Arrays.asList(9)), null);
        List<CellRange> plain = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true);
        assertEquals(plain.size(), withBreak.size(), "break at last row must not create an empty trailing page");
        assertEquals(plain.get(0).getLastRowIndex(), withBreak.get(0).getLastRowIndex());
    }

    @Test
    public void testBreakCombinesWithCapacityLimit() {
        // 容量 150pt=7 行/页；break after row 3 → 页1 止于 row3，其后按容量
        BaseTable table = fixture(20, 3, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 150, 60, 20, true,
                new HashSet<>(Arrays.asList(3)), null);
        assertEquals(3, regions.get(0).getLastRowIndex());
        assertEquals(4, regions.get(1).getFirstRowIndex());
        // 后续页按容量 7 行/页：rows 4-10, 11-17, 18-19
        assertEquals(10, regions.get(1).getLastRowIndex());
        // 1 (break page) + 3 capacity pages = 4 total
        assertEquals(4, regions.size());
        assertEquals(19, regions.get(3).getLastRowIndex());
    }

    @Test
    public void testZeroColumnTableTerminates() {
        // 回归钉（WI3 调试中发现）：0 列表在列分页分支曾死循环（startCol 永不推进）
        BaseTable table = new BaseTable();
        BaseRow row = new BaseRow();
        row.setHeight(20.0);
        table.getRows().add(row);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 300, 60, 20, true,
                new HashSet<>(Arrays.asList(0)), new HashSet<>(Arrays.asList(0)));
        assertEquals(0, regions.size());
    }

    @Test
    public void testRowOnlyPathHonorsBreaks() {
        BaseTable table = fixture(20, 3, 20);
        List<CellRange> regions = TableSplitHelper.splitTable(table, 400, 150, 60, 20, false,
                new HashSet<>(Arrays.asList(5)), null);
        assertEquals(5, regions.get(0).getLastRowIndex());
        assertEquals(6, regions.get(1).getFirstRowIndex());
    }
}
