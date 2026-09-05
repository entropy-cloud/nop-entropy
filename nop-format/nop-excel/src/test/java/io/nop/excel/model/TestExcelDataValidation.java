package io.nop.excel.model;

import io.nop.core.model.table.CellRange;
import io.nop.excel.model.constants.ExcelDataValidationType;
import io.nop.excel.model.constants.ExcelDataValidationOperator;
import io.nop.excel.model.constants.ExcelDataValidationErrorStyle;
import io.nop.excel.model.constants.ExcelDataValidationImeMode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestExcelDataValidation {

    @Test
    public void testAllChainedMethods() {
        List<String> listOptions = Arrays.asList("Option1", "Option2", "Option3");
        List<CellRange> ranges = Arrays.asList(CellRange.parseRangeList("A1:A10").get(0), CellRange.parseRangeList("B1:B10").get(0));
        
        ExcelDataValidation validation = new ExcelDataValidation()
                .type(ExcelDataValidationType.LIST)
                .allowBlank(true)
                .error("输入值不在允许的列表中")
                .errorStyle(ExcelDataValidationErrorStyle.STOP)
                .errorTitle("输入错误")
                .formula1("=Sheet1!$A$1:$A$3")
                .formula2("=Sheet1!$B$1")
                .id("validation1")
                .imeMode(ExcelDataValidationImeMode.ON)
                .operator(ExcelDataValidationOperator.BETWEEN)
                .prompt("请从列表中选择一个选项")
                .promptTitle("输入提示")
                .showErrorMessage(true)
                .showInputMessage(true)
                .sqref("A1:A10 B1:B10");
        
        // 验证所有属性设置正确
        assertEquals(ExcelDataValidationType.LIST, validation.getType());
        assertTrue(validation.getAllowBlank());
        assertEquals("输入值不在允许的列表中", validation.getError());
        assertEquals(ExcelDataValidationErrorStyle.STOP, validation.getErrorStyle());
        assertEquals("输入错误", validation.getErrorTitle());
        assertEquals("=Sheet1!$A$1:$A$3", validation.getFormula1());
        assertEquals("=Sheet1!$B$1", validation.getFormula2());
        assertEquals("validation1", validation.getId());
        assertEquals(ExcelDataValidationImeMode.ON, validation.getImeMode());
        assertEquals(ExcelDataValidationOperator.BETWEEN, validation.getOperator());
        assertEquals("请从列表中选择一个选项", validation.getPrompt());
        assertEquals("输入提示", validation.getPromptTitle());
        assertTrue(validation.getShowErrorMessage());
        assertTrue(validation.getShowInputMessage());
        assertEquals("A1:A10 B1:B10", validation.getSqref());
    }

    @Test
    public void testPartialChainedMethods() {
        // 测试部分方法链式调用
        ExcelDataValidation validation = new ExcelDataValidation()
                .type(ExcelDataValidationType.WHOLE)
                .operator(ExcelDataValidationOperator.GREATER_THAN)
                .formula1("10")
                .error("请输入大于10的整数");
        
        assertEquals(ExcelDataValidationType.WHOLE, validation.getType());
        assertEquals(ExcelDataValidationOperator.GREATER_THAN, validation.getOperator());
        assertEquals("10", validation.getFormula1());
        assertEquals("请输入大于10的整数", validation.getError());
    }

    @Test
    public void testNullValues() {
        // 测试null值处理
        ExcelDataValidation validation = new ExcelDataValidation()
                .type(null)
                .allowBlank(null)
                .error(null)
                .showErrorMessage(null);
        
        assertNull(validation.getType());
        assertNull(validation.getAllowBlank());
        assertNull(validation.getError());
        assertNull(validation.getShowErrorMessage());
    }

    /**
     * OOXML sqref 是空格分隔的引用列表（Excel 自身按空格写出）。
     * 修复前 getRanges() 按逗号切分，解析 Excel 生成的多区域校验直接抛类型转换异常
     */
    @Test
    public void testSpaceSeparatedSqrefRanges() {
        ExcelDataValidation validation = new ExcelDataValidation().sqref("A1:A10 B1:B10");

        List<CellRange> ranges = validation.getRanges();
        assertEquals(2, ranges.size());
        assertEquals(0, ranges.get(0).getFirstRowIndex());
        assertEquals(9, ranges.get(0).getLastRowIndex());
        assertEquals(0, ranges.get(0).getFirstColIndex());
        assertEquals(1, ranges.get(1).getFirstColIndex());
    }

    /**
     * 逗号分隔的历史数据同样兼容
     */
    @Test
    public void testCommaSeparatedSqrefRanges() {
        ExcelDataValidation validation = new ExcelDataValidation().sqref("A1:A10,B1:B10");
        assertEquals(2, validation.getRanges().size());
    }

    /**
     * setRanges 写出必须使用空格分隔，否则生成的 sqref 不是合法 ST_Sqref，
     * Excel 打开时报文件损坏
     */
    @Test
    public void testSetRangesJoinsWithSpace() {
        List<CellRange> ranges = Arrays.asList(
                new CellRange(0, 0, 9, 0),
                new CellRange(1, 2, 1, 2));
        ExcelDataValidation validation = new ExcelDataValidation().ranges(ranges);

        assertEquals("A1:A10 C2", validation.getSqref());
    }
}