package io.nop.ooxml.xlsx.util;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelCell;
import io.nop.excel.model.IExcelSheet;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ExcelTextHelper {
    public static void collectSheetUniqueText(IExcelSheet sheet, Set<String> texts) {
        texts.add(sheet.getName());

        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            if (!StringHelper.isBlank(cell.getComment()))
                texts.add(cell.getComment().trim());

            String text = cell.getText();
            if (!StringHelper.isBlank(text))
                texts.add(cell.getText().trim());

            return ProcessResult.CONTINUE;
        });
    }

    public static Set<String> getUniqueTexts(ExcelWorkbook wk) {
        Set<String> ret = new TreeSet<>();
        collectUniqueTexts(wk, ret);
        return ret;
    }

    public static void collectUniqueTexts(ExcelWorkbook wk, Set<String> texts) {
        wk.getSheets().forEach(sheet -> {
            collectSheetUniqueText(sheet, texts);
        });
    }

    public static void changeSheetTexts(IExcelSheet sheet, Map<String, String> texts) {
        String sheetName = getMappedText(texts, sheet.getName());
        if (sheetName != null)
            sheet.setName(sheetName);

        sheet.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            IExcelCell ec = (IExcelCell) cell;
            String comment = getMappedText(texts, cell.getComment());
            if (comment != null)
                ec.setComment(comment);

            String value = getMappedText(texts, ec.getText());
            if (value != null) {
                ec.setValue(value);
            }
            return ProcessResult.CONTINUE;
        });
    }

    static String getMappedText(Map<String, String> texts, String key) {
        if (key == null || key.isEmpty())
            return null;
        String trimmedKey = key.trim();
        String mapped = texts.get(trimmedKey);
        if (mapped == null)
            return mapped;
        return mapped;
    }
}