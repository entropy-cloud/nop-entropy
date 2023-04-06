/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.engine.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.model.table.html.HtmlTableOutput;
import io.nop.core.resource.tpl.ITemplateOutput;
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

public class HtmlReportRendererFactory implements IReportRendererFactory {
    static final Logger LOG = LoggerFactory.getLogger(HtmlTemplate.class);

    @Override
    public ITemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new HtmlTemplate(model, sheetGenerator);
    }

    static class HtmlTemplate implements ITextTemplateOutput {

        private final ExcelWorkbook model;
        private final IExcelSheetGenerator sheetGenerator;

        public HtmlTemplate(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
            this.model = model;
            this.sheetGenerator = sheetGenerator;
        }

        @Override
        public void generateToWriter(Writer out, IEvalContext context) throws IOException {
            long beginTime = CoreMetrics.currentTimeMillis();
            LOG.debug("nop.report.begin-generate-html");

            String scopeCssPrefix = getScopeCssPrefix(context);

            renderStyles(model.getStyles(), scopeCssPrefix, out);

            if (sheetGenerator != null) {
                sheetGenerator.generate(context, sheet -> {
                    renderSheet(sheet, scopeCssPrefix, out, context);
                });
            } else {
                model.getSheets().forEach(sheet -> {
                    renderSheet(sheet, scopeCssPrefix, out, context);
                });
            }
            out.flush();
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.report.end-generate-html:usedTime={}", endTime - beginTime);
        }

        private String getScopeCssPrefix(IEvalContext context) {
            String prefix = (String) context.getEvalScope().getLocalValue(XptConstants.VAR_SCOPED_CSS_PREFIX);
            if (StringHelper.isEmpty(prefix)) {
                prefix = XptConstants.CSS_PREFIX_SCOPED;
            }
            return prefix;
        }

        private void renderStyles(List<ExcelStyle> styles, String scopedCssPrefix, Writer out) throws IOException {
            out.write("<style>\n");
            out.append(".xpt-table{\n" +
                    "   border-collapse:collapse;border-spacing:0;table-layout:fixed;\n" +
                    "}\n" +
                    ".xpt-cell-num{ text-align:right; }\n" +
                    ".xpt-cell{\n" +
                    "   word-wrap:break-word;word-break:break-all;padding:2px;box-sizing:border-box;\n" +
                    "}\n");
            for (ExcelStyle style : styles) {
                renderStyle(style, scopedCssPrefix, out);
            }
            out.write("</style>\n");
        }

        private void renderStyle(ExcelStyle style, String scopedCssPrefix, Writer out) throws IOException {
            out.write(style.toCssStyle(scopedCssPrefix));
        }

        private void renderSheet(IExcelSheet sheet, String scopedCssPrefix, Writer out, IEvalContext context) {
            try {
                HtmlTableOutput output = new HtmlTableOutput(sheet.getTable(), XptConstants.CSS_PREFIX_XPT);
                output.setDefaultHeight(sheet.getDefaultRowHeight());
                output.setDefaultWidth(sheet.getDefaultColumnWidth());
                output.setScopeCssPrefix(scopedCssPrefix);
                output.generateToWriter(out, context);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }
}