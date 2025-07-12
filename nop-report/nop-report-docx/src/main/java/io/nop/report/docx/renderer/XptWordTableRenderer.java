package io.nop.report.docx.renderer;

import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.engine.ExpandedSheetGenerator;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.engine.XptRuntime;
import io.nop.report.core.model.ExpandedSheet;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;

public class XptWordTableRenderer implements ITextTemplateOutput {
    private final ExcelSheet xptModel;

    public XptWordTableRenderer(ExcelSheet xptModel) {
        this.xptModel = xptModel;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        XptRuntime xptRt = new XptRuntime(context.getEvalScope());
        ExcelWorkbook wk = new ExcelWorkbook();
        xptRt.setWorkbook(wk);

        ExpandedSheet sheet = new ExpandedSheetGenerator(wk).generateSheet(xptModel, xptRt, new HashMap<>());
        renderExpandedSheet(out, sheet, xptRt);
    }

    protected void renderExpandedSheet(Writer out, ExpandedSheet sheet, IXptRuntime xptRt) throws IOException {

    }
}
