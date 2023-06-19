package io.nop.ooxml.xlsx.util;

import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;

public class ExcelHelper {
    public static ExcelWorkbook parseExcel(IResource resource) {
        return new ExcelWorkbookParser().parseFromResource(resource);
    }

    public static void saveExcel(IResource resource, ExcelWorkbook workbook) {
        new ExcelTemplate(workbook, null).generateToResource(resource, DisabledEvalScope.INSTANCE);
    }
}