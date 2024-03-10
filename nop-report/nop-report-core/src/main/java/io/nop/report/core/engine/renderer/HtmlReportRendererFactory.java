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
import io.nop.excel.model.ExcelImage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class HtmlReportRendererFactory implements IReportRendererFactory {
    static final Logger LOG = LoggerFactory.getLogger(HtmlTemplate.class);

    @Override
    public ITextTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
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

            String reportId = getXptReportId(context);
            out.write("<div id=\"");
            out.write(reportId);
            out.write("\">\n");

            String scopeCssPrefix = getScopeCssPrefix(context);

            renderStyles(model.getStyles(), reportId, scopeCssPrefix, out);

            MutableInt index = new MutableInt();

            if (sheetGenerator != null) {
                sheetGenerator.generate(context, sheet -> {
                    index.incrementAndGet();
                    String sheetId = reportId + "-sheet-" + index;
                    renderSheet(sheet, sheetId, scopeCssPrefix, out, context);
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
            String reportId = (String) context.getEvalScope().getLocalValue(XptConstants.VAR_XPT_REPORT_ID);
            if (StringHelper.isEmpty(reportId)) {
                reportId = XptConstants.DEFAULT_XPT_REPORT_ID;
            }
            return reportId;
        }

        private String getScopeCssPrefix(IEvalContext context) {
            String prefix = (String) context.getEvalScope().getLocalValue(XptConstants.VAR_SCOPED_CSS_PREFIX);
            if (StringHelper.isEmpty(prefix)) {
                prefix = XptConstants.CSS_PREFIX_SCOPED;
            }
            return prefix;
        }

        private void renderStyles(List<ExcelStyle> styles, String reportId, String scopedCssPrefix, Writer out) throws IOException {
            out.write("<style>\n");
            out.append("#").append(reportId).append(' ');
            out.append(".xpt-table{\n" +
                    "   border-collapse:collapse;border-spacing:0;table-layout:fixed;\n" +
                    "}\n" + ("#" + reportId + " ") +
                    ".xpt-row{ line-height:normal}"+ // 前台如果引入ant-design-vue会导致行高不正确，需要这里重置一下
                    "}\n" + ("#" + reportId + " ") +
                    ".xpt-cell-num{ text-align:right; }\n" + ("#" + reportId + " ") +
                    ".xpt-cell{\n" +
                    "   word-wrap:break-word;word-break:break-all;padding:2px;box-sizing:border-box;\n" +
                    "}\n");
            for (ExcelStyle style : styles) {
                renderStyle(style, reportId, scopedCssPrefix, out);
            }
            out.write("</style>\n");
        }

        private void renderStyle(ExcelStyle style, String reportId, String scopedCssPrefix, Writer out) throws IOException {
            out.write("#");
            out.write(reportId);
            out.write(" ");
            out.write(style.toCssStyle(scopedCssPrefix));
        }

        private void renderSheet(IExcelSheet sheet, String sheetId, String scopedCssPrefix, Writer out, IEvalContext context) {
            try {
                out.write("<div id=\"");
                out.write(sheetId);
                out.write("\">\n");
                if (sheet.getImages() != null && !sheet.getImages().isEmpty()) {
                    out.write("<style>\n");
                    out.write("#");
                    out.write(sheetId);
                    out.write("{\n");

                    double minHeight = 0;
                    double minWidth = 0;

                    boolean notPrint = false;
                    for (ExcelImage image : sheet.getImages()) {
                        if (image.getData() == null)
                            continue;
                        if (!image.isPrint()) {
                            notPrint = true;
                        }

                        minHeight = Math.max(minHeight, image.getTop() + image.getHeight());
                        minWidth = Math.max(minWidth, image.getLeft() + image.getWidth());
                    }
                    out.write("min-height:" + minHeight + "pt;\n");
                    out.write("min-width:" + minWidth + "pt;\n");
                    outputBackground(sheet.getImages().stream().filter(image -> image.getData() != null), out);
                    out.write("}\n");

                    // 有不打印的图片
                    if (notPrint) {
                        out.write("@media print {\n");
                        out.write("#");
                        out.write(sheetId);
                        out.write("{\n");
                        outputBackground(sheet.getImages().stream().filter(image -> image.getData() != null && image.isPrint()), out);
                        out.write("}\n");
                        out.write("}\n");
                    }

                    out.write("</style>\n");
                }
                HtmlTableOutput output = new HtmlTableOutput(sheet.getTable(), XptConstants.CSS_PREFIX_XPT);
                output.setDefaultHeight(sheet.getDefaultRowHeight());
                output.setDefaultWidth(sheet.getDefaultColumnWidth());
                output.setScopeCssPrefix(scopedCssPrefix);
                output.generateToWriter(out, context);
                out.write("</div>\n");
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }


        private void outputBackground(Stream<ExcelImage> images, Writer out) throws IOException {
            List<String> urls = new ArrayList<>();
            List<String> sizes = new ArrayList<>();
            List<String> posList = new ArrayList<>();
            List<String> repeats = new ArrayList<>();

            images.forEach(image -> {
                String url = image.getData().toDataUrl(image.getMimeType());
                urls.add("url(" + url + ")");
                sizes.add(image.getWidth() + "pt " + image.getHeight() + "pt");
                posList.add(image.getLeft() + "pt " + image.getTop() + "pt");
                repeats.add("no-repeat");
            });

            out.write("background-image:");
            if (urls.isEmpty()) {
                out.write("none;\n");
                return;
            }

            out.write(StringHelper.join(urls, ","));
            out.write(";\n");
            out.write("background-size:");
            out.write(StringHelper.join(sizes, ","));
            out.write(";\n");
            out.write("background-position:");
            out.write(StringHelper.join(posList, ","));
            out.write(";\n");
            out.write("background-repeat:");
            out.write(StringHelper.join(repeats, ","));
            out.write(";\n");
        }
    }
}