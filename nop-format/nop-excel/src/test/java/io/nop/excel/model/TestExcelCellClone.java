package io.nop.excel.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * cloneInstance 必须与生成的 copyTo 字段集一致：
 * 修复前漏拷 linkUrl/name/protected，克隆表格丢失全部超链接
 */
public class TestExcelCellClone {

    @Test
    public void testCloneInstanceKeepsAllFields() {
        ExcelCell cell = new ExcelCell();
        cell.setValue("text");
        cell.setLinkUrl("http://example.com");
        cell.setName("cellName");
        cell.setProtected(true);
        cell.setFormula("=1+1");
        cell.setStyleId("s1");

        ExcelCell clone = cell.cloneInstance();
        assertEquals("text", clone.getValue());
        assertEquals("http://example.com", clone.getLinkUrl());
        assertEquals("cellName", clone.getName());
        assertEquals(Boolean.TRUE, clone.getProtected());
        assertEquals("=1+1", clone.getFormula());
        assertEquals("s1", clone.getStyleId());
    }

    @Test
    public void testToStringIncludesModelCellName() {
        ExcelCell cell = new ExcelCell();
        cell.setValue("v");
        XptCellModel model = new XptCellModel();
        model.setName("myCell");
        cell.setModel(model);
        // 修复前三元条件写反：名为null时打印",null"，有名时不打印
        String s = cell.toString();
        assertEquals(true, s.contains("myCell"));
        assertEquals(false, s.contains(",null"));
    }
}
