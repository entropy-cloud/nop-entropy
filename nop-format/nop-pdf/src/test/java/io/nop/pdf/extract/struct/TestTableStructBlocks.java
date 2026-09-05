package io.nop.pdf.extract.struct;

import io.nop.pdf.extract.data.DualMarkerTableLocator;
import io.nop.pdf.extract.table.DefaultTableMerger;
import org.junit.jupiter.api.Test;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表格结构相关缺陷的回归：
 * 1. TableCellBlock构造器此前丢弃rowSpan/colSpan参数（硬编码1），跨页合并表丢失全部跨行跨列
 * 2. TableBlock改用BaseTable后，跨行跨列的spanned位置是ProxyCell，getCell/getRowCells盲转TableCellBlock
 *    导致任何含合并单元格的表格触发ClassCastException
 * 3. BlockPointer遍历到最后一页末尾时NPE（调用方以null判终止）；跨3页以上表格被截断
 * 4. DualMarkerTableLocator.merge空表抛IOOBE、不可合并的表被静默丢弃
 */
public class TestTableStructBlocks {

    private static TextlineBlock textline(String content, double y) {
        TextlineBlock block = new TextlineBlock();
        block.setContent(content);
        block.setViewBounding(new Rectangle2D.Double(0, y, 100, 10));
        return block;
    }

    private static TableBlock table(int pageNo, double y) {
        TableBlock table = new TableBlock();
        table.setPageNo(pageNo);
        table.setViewBounding(new Rectangle2D.Double(0, y, 100, 50));
        List<Double> xs = new ArrayList<>();
        xs.add(0.0);
        xs.add(50.0);
        xs.add(100.0);
        table.setXpoints(xs);
        return table;
    }

    @Test
    public void testConstructorKeepsSpans() {
        TableCellBlock cell = new TableCellBlock(1, 2, 3, 4);
        assertEquals(3, cell.getRowspan());
        assertEquals(4, cell.getColspan());
        assertEquals(3, cell.getMergeAcross());
        assertEquals(2, cell.getMergeDown());
    }

    @Test
    public void testGetCellReturnsRealCellForProxyPositions() {
        TableBlock table = table(0, 0);
        TableCellBlock cell = new TableCellBlock(0, 0, 1, 2);
        cell.setContent("merged");
        table.addCell(0, 0, cell);

        // (0,1)是ProxyCell位置，修复前此处抛ClassCastException
        assertSame(cell, table.getCell(0, 0));
        assertSame(cell, table.getCell(0, 1));

        List<TableCellBlock> rowCells = table.getRowCells(0);
        assertEquals(2, rowCells.size());
        assertSame(cell, rowCells.get(0));
        assertSame(cell, rowCells.get(1));

        assertEquals(null, table.getCell(1, 0));
    }

    @Test
    public void testMergerKeepsCellSpans() {
        TableBlock t1 = table(0, 0);
        TableBlock t2 = table(1, 0);
        // 每页一个跨2列的表头单元格
        TableCellBlock c1 = new TableCellBlock(0, 0, 1, 2);
        c1.setContent("h");
        c1.setViewBounding(new Rectangle2D.Double(0, 0, 100, 10));
        t1.addCell(0, 0, c1);

        TableCellBlock c2 = new TableCellBlock(0, 0, 1, 2);
        c2.setContent("h2");
        c2.setViewBounding(new Rectangle2D.Double(0, 0, 100, 10));
        t2.addCell(0, 0, c2);

        TableBlock merged = new DefaultTableMerger().merge(new ResourceDocument(), t1, t2);
        assertEquals(2, merged.getColCount());
        // 修复前构造器丢弃span，合并结果全部退化为1x1
        TableCellBlock top = merged.getCell(0, 0);
        assertEquals(2, top.getColspan());
        assertEquals("h", top.getContent());
        assertEquals("h2", merged.getCell(1, 0).getContent());
    }

    @Test
    public void testNextBlockReturnsNullAtDocumentEnd() {
        ResourceDocument doc = new ResourceDocument();
        ResourcePage page = new ResourcePage();
        page.addTextline(textline("only", 0));
        List<ResourcePage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        BlockPointer pointer = new BlockPointer(doc).moveToBlock(-1);
        // 修复前遍历到最后一页末尾抛NPE而非返回false
        assertFalse(pointer.findTable(null));
    }

    @Test
    public void testCollectPageTablesAcrossThreePages() {
        ResourceDocument doc = new ResourceDocument();
        List<ResourcePage> pages = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ResourcePage page = new ResourcePage();
            page.addTable(table(i, i * 100));
            pages.add(page);
        }
        doc.setPages(pages);

        // blockIndex初始为0，nextBlock会从下一块开始，显式定位到首块之前
        BlockPointer pointer = new BlockPointer(doc).moveToBlock(-1);
        assertTrue(pointer.findTable(null));
        // 修复前持续与第一个表的页码比较，跨3页的表格只剩前两页
        List<TableBlock> collected = pointer.collectPageTables();
        assertEquals(3, collected.size());
        assertEquals(0, collected.get(0).getPageNo());
        assertEquals(2, collected.get(2).getPageNo());
    }

    @Test
    public void testLocateTablesWithNoTableBetweenMarkers() {
        ResourceDocument doc = new ResourceDocument();
        ResourcePage page = new ResourcePage();
        page.addTextline(textline("START", 0));
        page.addTextline(textline("END", 20));
        List<ResourcePage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        DualMarkerTableLocator locator = new DualMarkerTableLocator("START", "END");
        locator.setTableMerger(new DefaultTableMerger());
        // 修复前markers之间没有表格时merge里get(0)抛IndexOutOfBoundsException
        assertEquals(0, locator.locateTables(doc).size());
    }

    @Test
    public void testMergeKeepsNonMergeableTable() {
        ResourceDocument doc = new ResourceDocument();
        ResourcePage page = new ResourcePage();
        page.addTextline(textline("START", 0));
        TableBlock t1 = table(0, 50);
        t1.setXpoints(List.of(0.0, 30.0, 100.0));
        page.addTable(t1);
        // 不同列数的第二个表：不可合并
        TableBlock t2 = table(0, 200);
        t2.setXpoints(List.of(0.0, 20.0, 40.0, 100.0));
        page.addTable(t2);
        page.addTextline(textline("END", 400));
        List<ResourcePage> pages = new ArrayList<>();
        pages.add(page);
        doc.setPages(pages);

        DualMarkerTableLocator locator = new DualMarkerTableLocator("START", "END");
        locator.setTableMerger(new DefaultTableMerger());
        // 修复前不可合并的t2被静默丢弃，只返回1个表
        assertEquals(2, locator.locateTables(doc).size());
    }
}
