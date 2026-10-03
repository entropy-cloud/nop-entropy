package io.nop.pdf.extract.data;

import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TableCellBlock;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 表格单元格定位语义：
 * RCPathCellDataLocator 按行/列路径（通配符）定位；RowNameCellDataLocator 按行名
 * （精确或正则）+列偏移定位；路径不匹配时必须返回 null 而不是越界单元格
 */
public class TestCellDataLocators {

    /**
     * 构造 3x3 表格：
     * 项目   金额  数量
     * 收入   100   1
     * 合计   300   2
     */
    private static TableBlock table() {
        TableBlock table = new TableBlock();
        table.setViewBounding(new Rectangle2D.Double(0, 0, 300, 90));
        String[][] contents = {
                {"项目", "金额", "数量"},
                {"收入", "100", "1"},
                {"合计", "300", "2"}};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                TableCellBlock cell = new TableCellBlock(i, j, 1, 1);
                cell.setContent(contents[i][j]);
                cell.setViewBounding(new Rectangle2D.Double(j * 100, i * 30, 100, 30));
                table.addCell(i, j, cell);
            }
        }
        return table;
    }

    @Test
    public void testRcPathLocatorFindsRowAndColumn() {
        // 行路径 "/合计" 锚定第一列；列路径 "/金额" 锚定首行为列名的列
        RCPathCellDataLocator locator = new RCPathCellDataLocator("amount", "/合计", "/金额");
        TableCellBlock cell = locator.locate(new CellDataLocatorContext(), table());
        assertEquals("300", cell.getContent());
    }

    @Test
    public void testRcPathLocatorWithoutLeadingSlashMatchesAnyLevel() {
        // 不带前导 / 时模式允许跨层级匹配，同样命中
        RCPathCellDataLocator locator = new RCPathCellDataLocator("amount", "合计", "金额");
        assertEquals("300", locator.locate(new CellDataLocatorContext(), table()).getContent());
    }

    @Test
    public void testRcPathLocatorReturnsNullWhenRowMissing() {
        RCPathCellDataLocator locator = new RCPathCellDataLocator("amount", "/不存在", "/金额");
        assertNull(locator.locate(new CellDataLocatorContext(), table()));
    }

    @Test
    public void testRcPathLocatorReturnsNullWhenColumnMissing() {
        RCPathCellDataLocator locator = new RCPathCellDataLocator("amount", "/合计", "/不存在");
        assertNull(locator.locate(new CellDataLocatorContext(), table()));
    }

    @Test
    public void testRcPathLocatorFlatTableSpansMergedCells() {
        // 首列带跨行合并单元格时，平坦化后被合并位置都持有相同文本，行路径仍可命中
        TableBlock table = new TableBlock();
        table.setViewBounding(new Rectangle2D.Double(0, 0, 200, 90));
        TableCellBlock group = new TableCellBlock(0, 0, 2, 1);
        group.setContent("A组");
        group.setViewBounding(new Rectangle2D.Double(0, 0, 100, 60));
        table.addCell(0, 0, group);
        TableCellBlock other = new TableCellBlock(2, 0, 1, 1);
        other.setContent("B组");
        other.setViewBounding(new Rectangle2D.Double(0, 60, 100, 30));
        table.addCell(2, 0, other);
        TableCellBlock v1 = new TableCellBlock(0, 1, 1, 1);
        v1.setContent("10");
        v1.setViewBounding(new Rectangle2D.Double(100, 0, 100, 30));
        table.addCell(0, 1, v1);
        TableCellBlock v2 = new TableCellBlock(1, 1, 1, 1);
        v2.setContent("20");
        v2.setViewBounding(new Rectangle2D.Double(100, 30, 100, 30));
        table.addCell(1, 1, v2);

        RCPathCellDataLocator locator = new RCPathCellDataLocator("v", "/A组", "/10");
        CellDataLocatorContext ctx = new CellDataLocatorContext();
        // 行路径命中合并组首行，列路径命中数据列首行
        assertEquals("10", locator.locate(ctx, table).getContent());
    }

    @Test
    public void testRcPathLocatorDoesNotClobberRowsBelowMergedCell() {
        // 回归覆盖 wi10#5 双重展开（plan 2306 项 7）：getCell 对被覆盖坐标返回锚点单元格，
        // 修复前 (1,0) 处二次展开把 (2,0) 的真实文本 "B组" 覆盖为 "A组"，行路径无法命中
        TableBlock table = new TableBlock();
        table.setViewBounding(new Rectangle2D.Double(0, 0, 200, 90));
        TableCellBlock group = new TableCellBlock(0, 0, 2, 1);
        group.setContent("A组");
        group.setViewBounding(new Rectangle2D.Double(0, 0, 100, 60));
        table.addCell(0, 0, group);
        TableCellBlock b = new TableCellBlock(2, 0, 1, 1);
        b.setContent("B组");
        b.setViewBounding(new Rectangle2D.Double(0, 60, 100, 30));
        table.addCell(2, 0, b);
        TableCellBlock v1 = new TableCellBlock(0, 1, 1, 1);
        v1.setContent("1");
        v1.setViewBounding(new Rectangle2D.Double(100, 0, 100, 30));
        table.addCell(0, 1, v1);
        TableCellBlock v2 = new TableCellBlock(1, 1, 1, 1);
        v2.setContent("2");
        v2.setViewBounding(new Rectangle2D.Double(100, 30, 100, 30));
        table.addCell(1, 1, v2);
        TableCellBlock v3 = new TableCellBlock(2, 1, 1, 1);
        v3.setContent("3");
        v3.setViewBounding(new Rectangle2D.Double(100, 60, 100, 30));
        table.addCell(2, 1, v3);

        RCPathCellDataLocator locator = new RCPathCellDataLocator("v", "/B组", "/3");
        assertEquals("3", locator.locate(new CellDataLocatorContext(), table).getContent(),
                "B组 行不能被上方合并单元格的二次展开覆盖");
    }

    @Test
    public void testRcPathLocatorToleratesMergedCellAtTableEdge() {
        // 回归覆盖 wi10#5 贴边越界（plan 2306 项 7）：合并区锚点在最后一行/列且
        // rowspan/colspan 触及表边界时，平坦化不得抛 ArrayIndexOutOfBoundsException
        TableBlock table = new TableBlock();
        table.setViewBounding(new Rectangle2D.Double(0, 0, 200, 60));
        TableCellBlock edge = new TableCellBlock(1, 1, 2, 2);
        edge.setContent("右下合并");
        edge.setViewBounding(new Rectangle2D.Double(100, 30, 100, 30));
        table.addCell(1, 1, edge);
        TableCellBlock a = new TableCellBlock(0, 0, 1, 1);
        a.setContent("A");
        a.setViewBounding(new Rectangle2D.Double(0, 0, 100, 30));
        table.addCell(0, 0, a);
        TableCellBlock b = new TableCellBlock(0, 1, 1, 1);
        b.setContent("B");
        b.setViewBounding(new Rectangle2D.Double(100, 0, 100, 30));
        table.addCell(0, 1, b);
        TableCellBlock c = new TableCellBlock(1, 0, 1, 1);
        c.setContent("C");
        c.setViewBounding(new Rectangle2D.Double(0, 30, 100, 30));
        table.addCell(1, 0, c);

        // 贴边单元格仍参与平坦化：行路径命中首列 "C" 所在行，列路径命中其数据 "B"
        RCPathCellDataLocator locator = new RCPathCellDataLocator("v", "/C", "/B");
        TableCellBlock cell = locator.locate(new CellDataLocatorContext(), table);
        assertNotNull(cell);
        assertEquals("B", cell.getContent());
    }

    @Test
    public void testRowNameLocatorExactMatch() {
        RowNameCellDataLocator locator = new RowNameCellDataLocator("sum", "合计", true);
        locator.setRowNameColIndex(0);
        locator.setDataColOffset(1);
        assertEquals("300", locator.locate(new CellDataLocatorContext(), table()).getContent());
    }

    @Test
    public void testRowNameLocatorRegexMatch() {
        RowNameCellDataLocator locator = new RowNameCellDataLocator("sum", "合.*", false);
        locator.setRowNameColIndex(0);
        locator.setDataColOffset(1);
        assertEquals("300", locator.locate(new CellDataLocatorContext(), table()).getContent());
    }

    @Test
    public void testRowNameLocatorExactModeRejectsPartialText() {
        // 单元格文本带额外字样时精确匹配失败；正则模式仍可命中
        TableBlock table = table();
        TableCellBlock cell = new TableCellBlock(2, 0, 1, 1);
        cell.setContent("合计 行");
        cell.setViewBounding(new Rectangle2D.Double(0, 60, 100, 30));
        table.addCell(2, 0, cell);

        RowNameCellDataLocator exact = new RowNameCellDataLocator("sum", "合计", true);
        exact.setRowNameColIndex(0);
        assertNull(exact.locate(new CellDataLocatorContext(), table));

        RowNameCellDataLocator regex = new RowNameCellDataLocator("sum", "合.*", false);
        regex.setRowNameColIndex(0);
        assertSame(table.getCell(2, 1), regex.locate(new CellDataLocatorContext(), table));
    }

    @Test
    public void testRowNameLocatorReturnsNullWhenNoRowMatches() {
        RowNameCellDataLocator locator = new RowNameCellDataLocator("sum", "不存在", false);
        locator.setRowNameColIndex(0);
        assertNull(locator.locate(new CellDataLocatorContext(), table()));
    }
}
