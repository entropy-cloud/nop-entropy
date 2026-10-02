package io.nop.office.doc.model;

import io.nop.api.core.util.ProcessResult;
import io.nop.api.core.util.SourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Word 文档模型结构语义：WordTable 的行列懒创建、合并单元格代理、深克隆与列配置；
 * OfficeDocModel.makeModel 惰性创建模板模型；WordTableCell 代理与取值语义
 */
public class TestWordTableStructure {

    @Test
    public void testNewTableStartsEmpty() {
        WordTable table = new WordTable();
        assertEquals(0, table.getRowCount());
        assertTrue(table.getCols().isEmpty());
        assertNull(table.getCell(0, 0));
    }

    @Test
    public void testMakeRowLazilyGrowsRows() {
        WordTable table = new WordTable();
        WordTableRow row = table.makeRow(2);
        assertEquals(3, table.getRowCount());
        assertSame(row, table.getRow(2));
        // newRow 工厂必须产出 WordTableRow 类型
        assertTrue(table.getRow(0) instanceof WordTableRow);
    }

    @Test
    public void testSetCellCreatesProxyForMergedPositions() {
        WordTable table = new WordTable();
        WordTableCell cell = table.newCell();
        cell.setValue("merged");
        cell.setMergeAcross(1);
        cell.setMergeDown(1);
        table.setCell(0, 0, cell);

        assertEquals("merged", table.getCell(0, 0).getText());
        // (0,1)/(1,0)/(1,1) 均为代理位：getCell 返回携带偏移的代理单元格，getRealCell 解析回真实单元格
        WordTableCell proxy01 = (WordTableCell) table.getCell(0, 1);
        assertTrue(proxy01.isProxyCell());
        assertSame(cell, proxy01.getRealCell());
        assertEquals(0, proxy01.getRowOffset());
        assertEquals(1, proxy01.getColOffset());

        WordTableCell proxy11 = (WordTableCell) table.getCell(1, 1);
        assertTrue(proxy11.isProxyCell());
        assertSame(cell, proxy11.getRealCell());
        assertEquals(1, proxy11.getRowOffset());
        assertEquals(1, proxy11.getColOffset());

        // 非代理单元格 getRealCell 返回自身
        assertFalse(cell.isProxyCell());
        assertSame(cell, cell.getRealCell());
    }

    @Test
    public void testCloneInstanceDeepCopiesRowsAndCells() {
        WordTable table = new WordTable();
        table.setId("t1");
        table.setStyleId("s1");
        WordTableCell cell = table.newCell();
        cell.setValue("v1");
        table.setCell(0, 0, cell);

        WordTable clone = table.cloneInstance();
        assertEquals("t1", clone.getId());
        assertEquals("s1", clone.getStyleId());
        assertEquals(1, clone.getRowCount());
        assertEquals("v1", clone.getCell(0, 0).getText());
        // 深克隆：单元格实例不同，修改克隆不影响原表
        assertNotSameCell(table.getCell(0, 0), clone.getCell(0, 0));
        clone.getCell(0, 0).setValue("changed");
        assertEquals("v1", table.getCell(0, 0).getText());
    }

    private static void assertNotSameCell(Object a, Object b) {
        assertFalse(a == b);
    }

    @Test
    public void testAddColumnCopiesConfig() {
        WordTable table = new WordTable();
        io.nop.core.model.table.impl.BaseColumnConfig config = new io.nop.core.model.table.impl.BaseColumnConfig();
        config.setWidth(120d);
        config.setHidden(true);
        config.setStyleId("cs1");
        table.addColumn(config);

        assertEquals(1, table.getCols().size());
        WordTableColumnConfig col = table.getCols().get(0);
        assertEquals(120d, col.getWidth(), 1e-9);
        assertEquals(true, col.isHidden());
        assertEquals("cs1", col.getStyleId());
    }

    @Test
    public void testDocModelMakeModelLazyAndStable() {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setLocation(SourceLocation.fromPath("/test/doc-model.xml"));
        assertNull(doc.getModel());
        OfficeDocTemplateModel model = doc.makeModel();
        assertNotNull(model);
        assertEquals("/test/doc-model.xml", model.getLocation().getPath());
        // 第二次调用复用同一实例
        assertSame(model, doc.makeModel());
    }

    @Test
    public void testTableCellTextAndClone() {
        WordTableCell cell = new WordTableCell();
        cell.setValue(42);
        assertEquals("42", cell.getText());
        cell.setValue(null);
        assertNull(cell.getText());
        assertNull(cell.getFormula());

        cell.setValue("x");
        cell.setMergeAcross(2);
        cell.setMergeDown(3);
        WordTableCell cloned = cell.cloneInstance();
        assertEquals("x", cloned.getText());
        assertEquals(2, cloned.getMergeAcross());
        assertEquals(3, cloned.getMergeDown());
    }

    @Test
    public void testParagraphAndPageModelsAttachToDoc() {
        OfficeDocModel doc = new OfficeDocModel();
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("p1");
        doc.addPage(page);
        assertTrue(doc.hasPage("p1"));
        assertSame(page, doc.getPage("p1"));

        OfficeParagraphModel paragraph = new OfficeParagraphModel();
        OfficeParagraphTemplateModel model = paragraph.makeModel();
        assertNotNull(model);
        assertSame(model, paragraph.getModel());

        // forEachRealCell 只遍历真实单元格（代理位不重复出现）
        WordTable table = new WordTable();
        WordTableCell a = table.newCell();
        a.setValue("a");
        a.setMergeAcross(1);
        table.setCell(0, 0, a);
        WordTableCell b = table.newCell();
        b.setValue("b");
        table.setCell(0, 2, b);
        StringBuilder seen = new StringBuilder();
        table.forEachRealCell((c, r, col) -> {
            seen.append(((WordTableCell) c).getText());
            return ProcessResult.CONTINUE;
        });
        assertEquals("ab", seen.toString());
    }
}
