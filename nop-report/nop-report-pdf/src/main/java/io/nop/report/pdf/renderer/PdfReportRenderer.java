/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.renderer;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.renderer.IExcelSheetGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class PdfReportRenderer implements IBinaryTemplateOutput {
    static final Logger LOG = LoggerFactory.getLogger(PdfReportRenderer.class);

    private final ExcelWorkbook model;
    private final IExcelSheetGenerator sheetGenerator;

    private final PdfRenderer renderer;


    public PdfReportRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        this.model = model;
        this.sheetGenerator = sheetGenerator;
        this.renderer = new PdfRenderer(new PDDocument());
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        long beginTime = CoreMetrics.currentTimeMillis();
        LOG.debug("nop.report.begin-generate-pdf");


        if (sheetGenerator != null) {
            sheetGenerator.generate(context, this::renderSheet);
        } else {
            model.getSheets().forEach(sheet -> {
                renderSheet(sheet, context);
            });
        }
        renderer.saveToStream(os);

        long endTime = CoreMetrics.currentTimeMillis();
        LOG.info("nop.report.end-generate-pdf:usedTime={}", endTime - beginTime);
    }


    private void renderSheet(IExcelSheet sheet, IEvalContext context) {
        new PdfSheetRenderer(renderer, model).renderSheet(sheet);
    }
}