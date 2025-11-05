package io.nop.excel.model;

public interface IExcelStyleProvider {
    ExcelStyle getStyle(String styleId);

    ExcelStyle getDefaultStyle();

    ExcelFont getDefaultFont();
}