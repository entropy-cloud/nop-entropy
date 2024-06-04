package io.nop.report.demo;

import io.nop.core.model.table.ITableView;
import io.nop.excel.model.IExcelSheet;

public class TableValidateHelper {
    public static void validateSheet(IExcelSheet sheet, String pattern) {
        ITableView table = sheet.getTable();

        System.out.println("pattern:" + pattern);
    }
}
