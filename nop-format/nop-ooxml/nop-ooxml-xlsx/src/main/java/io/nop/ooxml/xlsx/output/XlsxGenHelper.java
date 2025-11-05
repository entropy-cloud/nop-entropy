package io.nop.ooxml.xlsx.output;

import io.nop.excel.model.ExcelWorkbook;

import static io.nop.excel.ExcelConfigs.CFG_EXCEL_MAX_SHEET_NAME_LENGTH;

public class XlsxGenHelper {
    /**
     * Excel中sheetName的长度不能超过31
     */
    public static String normalizeSheetName(String sheetName, int index, ExcelWorkbook workbook) {
        int maxSheetNameLength = CFG_EXCEL_MAX_SHEET_NAME_LENGTH.get();
        if (workbook.getModel() != null && workbook.getModel().getMaxSheetNameLength() != null && workbook.getModel().getMaxSheetNameLength() > 31) {
            maxSheetNameLength = workbook.getModel().getMaxSheetNameLength();
        }

        if (sheetName.length() <= maxSheetNameLength)
            return sheetName;

        String postfix = "_" + (index + 1);
        return sheetName.substring(0, maxSheetNameLength - postfix.length()) + postfix;
    }

}
