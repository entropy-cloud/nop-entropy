package io.nop.excel.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.util.ExcelModelHelper;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 工作簿模型解析语义（workbook.xdef）：公式模型、合并单元格、链接在 XNode 序列化与重解析后保持不变；
 * ExcelModelHelper 的模型拷贝保留公式等单元格语义字段。模块内不涉及公式求值（求值在 POI/nop-report 侧）。
 */
public class TestExcelWorkbookFormulaModel {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static ExcelWorkbook buildWorkbookWithFormula() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("data");
        wk.addSheet(sheet);

        ExcelCell a1 = new ExcelCell();
        a1.setValue(1);
        sheet.getTable().setCell(0, 0, a1);

        ExcelCell b1 = new ExcelCell();
        b1.setValue(2);
        sheet.getTable().setCell(0, 1, b1);

        // 公式模型：c1 = SUM(A1:B1)，模块层只存储公式文本
        ExcelCell c1 = new ExcelCell();
        c1.setValue(null);
        c1.setFormula("SUM(A1:B1)");
        c1.setMergeDown(1);
        sheet.getTable().setCell(0, 2, c1);

        return wk;
    }

    // 公式/合并/数值经 dslModelToXNode -> DslModelParser 重解析后语义不变
    @Test
    public void testFormulaCellSurvivesModelRoundtrip() {
        ExcelWorkbook wk = buildWorkbookWithFormula();

        XNode node = DslModelHelper.dslModelToXNode("/nop/schema/excel/workbook.xdef", wk);
        ExcelWorkbook reparsed = (ExcelWorkbook) new DslModelParser().parseFromNode(node);

        ExcelSheet sheet = reparsed.getSheet("data");
        assertNotNull(sheet);
        assertEquals("data", sheet.getName());

        ExcelCell a1 = (ExcelCell) sheet.getTable().getCell(0, 0);
        // 数值经 XNode 文本序列化后以字符串形式还原（模型层不做类型反推）
        assertEquals("1", String.valueOf(a1.getValue()));

        ExcelCell c1 = (ExcelCell) sheet.getTable().getCell(0, 2);
        assertEquals("SUM(A1:B1)", c1.getFormula());
        assertEquals(1, c1.getMergeDown());
    }

    // copySheet 深拷贝保留公式、合并、链接与样式引用
    @Test
    public void testCopySheetPreservesFormulaSemantics() {
        ExcelWorkbook wk = buildWorkbookWithFormula();
        ExcelSheet sheet = wk.getSheet("data");
        ExcelCell a0 = (ExcelCell) sheet.getTable().getCell(0, 0);
        a0.setLinkUrl("http://example.com");
        a0.setStyleId("st1");

        ExcelSheet copy = ExcelModelHelper.copySheet(sheet);

        assertEquals("data", copy.getName());
        ExcelCell a1 = (ExcelCell) copy.getTable().getCell(0, 0);
        assertEquals(1, a1.getValue());
        assertEquals("http://example.com", a1.getLinkUrl());
        assertEquals("st1", a1.getStyleId());

        ExcelCell c1 = (ExcelCell) copy.getTable().getCell(0, 2);
        assertEquals("SUM(A1:B1)", c1.getFormula());
        assertEquals(1, c1.getMergeDown());

        // 拷贝是深拷贝：修改副本不影响原模型
        c1.setFormula("CHANGED");
        assertEquals("SUM(A1:B1)", sheet.getTable().getCell(0, 2).getFormula());
    }

    // getSheet 按名称定位，addSheet 保持插入顺序
    @Test
    public void testSheetOrderAndLookup() {
        ExcelWorkbook wk = new ExcelWorkbook();
        ExcelSheet s1 = new ExcelSheet();
        s1.setName("one");
        ExcelSheet s2 = new ExcelSheet();
        s2.setName("two");
        wk.addSheet(s1);
        wk.addSheet(s2);

        assertSame(s1, wk.getSheet("one"));
        assertSame(s2, wk.getSheet("two"));
        assertEquals(2, wk.getSheets().size());
        assertEquals("one", wk.getSheets().get(0).getName());
    }
}
