/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.mutable.MutableInt;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.model.table.html.HtmlTableOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class HtmlReportRendererFactory implements IReportRendererFactory {
    static final Logger LOG = LoggerFactory.getLogger(HtmlTemplate.class);

    @Override
    public ITextTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new HtmlTemplate(model, sheetGenerator);
    }

    public static class HtmlTemplate implements ITextTemplateOutput {

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

            String reportId = getXptReportId(context);
            out.write("<div id=\"");
            out.write(reportId);
            out.write("\">\n");

            String scopeCssPrefix = getScopeCssPrefix(context);

            renderStyles(model.getStyles(), reportId, scopeCssPrefix, out);

            MutableInt index = new MutableInt();

            if (sheetGenerator != null) {
                sheetGenerator.generate(context, (sheet, ctx) -> {
                    index.incrementAndGet();
                    String sheetId = reportId + "-sheet-" + index;
                    renderSheet(sheet, sheetId, scopeCssPrefix, out, ctx);
                });
            } else {
                model.getSheets().forEach(sheet -> {
                    index.incrementAndGet();
                    String sheetId = reportId + "-sheet-" + index;
                    renderSheet(sheet, sheetId, scopeCssPrefix, out, context);
                });
            }
            out.write("</div>\n");
            out.flush();
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.report.end-generate-html:usedTime={}", endTime - beginTime);
        }

        private String getXptReportId(IEvalContext context) {
            String reportId = (String) context.getEvalScope().getLocalValue(ExcelConstants.VAR_XPT_REPORT_ID);
            if (StringHelper.isEmpty(reportId)) {
                reportId = ExcelConstants.DEFAULT_XPT_REPORT_ID;
            }
            return reportId;
        }

        protected String getScopeCssPrefix(IEvalContext context) {
            String prefix = (String) context.getEvalScope().getLocalValue(ExcelConstants.VAR_SCOPED_CSS_PREFIX);
            if (StringHelper.isEmpty(prefix)) {
                prefix = ExcelConstants.CSS_PREFIX_SCOPED;
            }
            return prefix;
        }

        protected void renderStyles(List<ExcelStyle> styles, String reportId, String scopedCssPrefix, Writer out) throws IOException {
            out.write("<style>\n");
            out.append("#").append(reportId).append(' ');
            out.append(".xpt-hidden{ display:none}\n");
            out.append("#").append(reportId).append(' ');
            out.append(".xpt-table{\n" +
                    "   border-collapse:collapse;border-spacing:0;table-layout:fixed;\n" +
                    "}\n" + ("#" + reportId + " ") +
                    ".xpt-row{ line-height:normal;}\n" + // 前台如果引入ant-design-vue会导致行高不正确，需要这里重置一下
                    ("#" + reportId + " ") +
                    ".xpt-cell-num{ text-align:right; }\n" + ("#" + reportId + " ") +
                    ".xpt-cell{\n" +
                    "   word-wrap:break-word;word-break:break-all;padding:2px;box-sizing:border-box;\n" +
                    "}\n");
            for (ExcelStyle style : styles) {
                renderStyle(style, reportId, scopedCssPrefix, out);
            }
            out.write("</style>\n");
        }

        protected void renderStyle(ExcelStyle style, String reportId, String scopedCssPrefix, Writer out) throws IOException {
            out.write("#");
            out.write(reportId);
            out.write(" ");
            out.write(style.toCssStyle(scopedCssPrefix));
        }

        protected void renderSheet(IExcelSheet sheet, String sheetId, String scopedCssPrefix, Writer out, IEvalContext context) {
            try {
                out.write("<div id=\"");
                out.write(sheetId);
                out.write("\" data-sheet-name=\"");
                out.write(StringHelper.escapeXmlAttr(sheet.getName()));
                out.write("\">\n");
                HtmlSheetVisualRenderer.renderVisualOverlays(sheet, sheetId, out);
                if (HtmlPagePagination.isPaginated(sheet)) {
                    renderPaginatedSheet(sheet, sheetId, scopedCssPrefix, out, context);
                } else {
                    HtmlTableOutput output = new HtmlTableOutput(sheet.getTable(), ExcelConstants.CSS_PREFIX_XPT);
                    output.setDefaultHeight(sheet.getDefaultRowHeight());
                    output.setDefaultWidth(sheet.getDefaultColumnWidth());
                    output.setScopeCssPrefix(scopedCssPrefix);
                    output.generateToWriter(out, context);
                }
                out.write("</div>\n");
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }

        /**
         * 打印分页输出（sheet 配置了 pageSetup 时）：@page CSS + 分页节（每节复制表头行进 thead、页脚 &P/&N）。
         * 见 ai-dev/design/nop-report/html-pagination.md。
         */
        protected void renderPaginatedSheet(IExcelSheet sheet, String sheetId, String scopedCssPrefix, Writer out, IEvalContext context) throws IOException {
            out.write("<style>\n");
            out.write(HtmlPagePagination.buildPageCss(sheet));
            out.write("#" + sheetId + " .xpt-page-footer{ text-align:center;font-size:10pt;color:#444;}\n");
            out.write("@media print{\n");
            out.write("#" + sheetId + " .xpt-page{ page-break-after:always;}\n");
            out.write("#" + sheetId + " .xpt-page:last-child{ page-break-after:auto;}\n");
            out.write("}\n");
            out.write("</style>\n");

            List<int[]> pages = HtmlPagePagination.packPages(sheet);
            String footerTemplate = HtmlPagePagination.footerTemplate(sheet);
            int pageNo = 1;
            for (int[] page : pages) {
                out.write("<div class=\"xpt-page\" data-page=\"");
                out.write(String.valueOf(pageNo));
                out.write("\">\n");

                HtmlTableOutput output = new HtmlTableOutput(sheet.getTable(), ExcelConstants.CSS_PREFIX_XPT);
                output.setDefaultHeight(sheet.getDefaultRowHeight());
                output.setDefaultWidth(sheet.getDefaultColumnWidth());
                output.setScopeCssPrefix(scopedCssPrefix);
                output.setRowRange(page[0], page[1]);
                output.setWriteTfoot(false);
                output.generateToWriter(out, context);

                out.write("<div class=\"xpt-page-footer\">");
                out.write(StringHelper.escapeXmlAttr(
                        HtmlPagePagination.renderFooter(footerTemplate, pageNo, pages.size())));
                out.write("</div>\n");

                out.write("</div>\n");
                pageNo++;
            }
        }

        protected void renderImages(IExcelSheet sheet, String sheetId, Writer out) throws IOException {
            HtmlSheetVisualRenderer.renderImages(sheetId, out,
                    sheet.getImages() == null ? new ArrayList<>() : sheet.getImages());
        }
    }
}