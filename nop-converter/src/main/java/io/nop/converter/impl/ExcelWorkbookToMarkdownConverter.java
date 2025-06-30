package io.nop.converter.impl;

import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.markdown.simple.TableToMarkdownConverter;

import java.util.List;

public class ExcelWorkbookToMarkdownConverter {

    public String convertToMarkdown(ExcelWorkbook wk) {
        StringBuilder sb = new StringBuilder();
        convertSheetsToMarkdown(wk.getSheets(), sb);
        return sb.toString();
    }

    public void convertSheetsToMarkdown(List<? extends IExcelSheet> sheets, StringBuilder md) {
        if (sheets == null || sheets.isEmpty()) {
            return;
        }

        for (IExcelSheet sheet : sheets) {
            convertSheetToMarkdown(sheet, md);
        }
    }

    public void convertSheetToMarkdown(IExcelSheet sheet, StringBuilder md) {
        md.append("# ").append(sheet.getName()).append("\n\n");
        TableToMarkdownConverter.INSTANCE.convertToMarkdown(sheet.getTable(), md);
    }
}