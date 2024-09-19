package io.nop.report.core.engine.renderer;

import io.nop.core.context.IEvalContext;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.ExcelModelHelper;
import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;

import java.util.ArrayList;

public class ReportRenderHelper {
    public static ExcelWorkbook renderModel(ExcelWorkbook wk, IExcelSheetGenerator sheetGenerator, IEvalContext ctx) {
        ExcelWorkbook ret = wk.cloneInstance();
        ret.setSheets(new ArrayList<>());

        sheetGenerator.generate(ctx, (sheet, context) -> {
            ExcelSheet copy = ExcelModelHelper.copySheet(sheet);
            ret.addSheet(copy);
        });
        return ret;
    }
}
