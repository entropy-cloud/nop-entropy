package io.nop.report.pdf.renderer;

import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;
import io.nop.report.core.engine.IReportRendererFactory;

public class PdfReportRendererFactory implements IReportRendererFactory {

    @Override
    public ITemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new PdfReportRenderer(model,sheetGenerator);
    }
}
