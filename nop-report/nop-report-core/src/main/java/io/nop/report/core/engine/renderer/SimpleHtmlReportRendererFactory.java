/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.model.table.html.HtmlTableOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.ooxml.xlsx.output.IExcelSheetGenerator;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportRendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class SimpleHtmlReportRendererFactory implements IReportRendererFactory {
    static final Logger LOG = LoggerFactory.getLogger(SimpleHtmlTemplate.class);

    @Override
    public ITextTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new SimpleHtmlTemplate(model, sheetGenerator);
    }

    public static class SimpleHtmlTemplate implements ITextTemplateOutput {

        private final ExcelWorkbook model;
        private final IExcelSheetGenerator sheetGenerator;

        public SimpleHtmlTemplate(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
            this.model = model;
            this.sheetGenerator = sheetGenerator;
        }

        @Override
        public void generateToWriter(Writer out, IEvalContext context) throws IOException {
            long beginTime = CoreMetrics.currentTimeMillis();
            LOG.debug("nop.report.begin-generate-html");

            String reportId = getXptReportId(context);

            out.write("<div id=\"");
            out.write(reportId);
            out.write("\">\n");

            renderStyles(model.getStyles(), reportId, out);

            MutableInt index = new MutableInt();

            if (sheetGenerator != null) {
                sheetGenerator.generate(context, (sheet, ctx) -> {
                    index.incrementAndGet();
                    String sheetId = reportId + "-sheet-" + index;
                    renderSheet(sheet, sheetId, out, ctx);
                });
            } else {
                model.getSheets().forEach(sheet -> {
                    index.incrementAndGet();
                    String sheetId = reportId + "-sheet-" + index;
                    renderSheet(sheet, sheetId, out, context);
                });
            }
            out.flush();
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.report.end-generate-html:usedTime={}", endTime - beginTime);
        }

        private String getXptReportId(IEvalContext context) {
            String reportId = (String) context.getEvalScope().getLocalValue(XptConstants.VAR_XPT_REPORT_ID);
            if (StringHelper.isEmpty(reportId)) {
                reportId = XptConstants.DEFAULT_XPT_REPORT_ID;
            }
            return reportId;
        }

        protected void renderStyles(List<ExcelStyle> styles, String reportId,  Writer out) throws IOException {
            out.write("<style>\n");
            out.append("table{\n" +
                    "   border-collapse:collapse;border-spacing:0;\n" +
                    "}\ntd{border:1px solid black;}");
            out.write("</style>\n");
        }

        protected void renderSheet(IExcelSheet sheet, String sheetId, Writer out, IEvalContext context) {
            try {
                out.write("<div id=\"");
                out.write(sheetId);
                out.write("\" data-sheet-name=\"");
                out.write(StringHelper.escapeXmlAttr(sheet.getName()));
                out.write("\">\n");
                HtmlTableOutput output = new HtmlTableOutput(sheet.getTable(), XptConstants.CSS_PREFIX_XPT);
                output.setDisableStyle(true);
                output.generateToWriter(out, context);
                out.write("</div>\n");
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }
}