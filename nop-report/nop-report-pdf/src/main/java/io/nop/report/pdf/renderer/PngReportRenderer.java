/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelSheetGenerator;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PNG 渲染器（renderType=png）：报表渲染为 PDF 后 PDFBox 逐页转 PNG（110dpi，与视觉验收口径一致），
 * 多页输出 zip（page-1.png...）。见 ai-dev/design/nop-report/export-formats.md。
 */
public class PngReportRenderer implements IBinaryTemplateOutput {
    private final PdfReportRenderer pdfRenderer;

    public PngReportRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        this.pdfRenderer = new PdfReportRenderer(model, sheetGenerator);
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) {
        // 复用 pdf 渲染：先在内存中生成 PDF，再逐页转 PNG
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        try {
            pdfRenderer.generateToStream(pdfBytes, context);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }

        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                     org.apache.pdfbox.Loader.loadPDF(pdfBytes.toByteArray()); // throws IOException
             ZipOutputStream zip = new ZipOutputStream(os)) {
            PDFRenderer pdfRenderer2 = new PDFRenderer(doc);
            for (int i = 0, n = doc.getNumberOfPages(); i < n; i++) {
                java.awt.image.BufferedImage image = pdfRenderer2.renderImageWithDPI(i, 110);
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(image, "png", png);
                zip.putNextEntry(new ZipEntry("page-" + (i + 1) + ".png"));
                zip.write(png.toByteArray());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void generateToFile(File file, IEvalContext context) {
        try (OutputStream os = new java.io.FileOutputStream(file)) {
            generateToStream(os, context);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void generateToResource(io.nop.core.resource.IResource resource, IEvalContext context) {
        try (OutputStream os = resource.getOutputStream()) {
            generateToStream(os, context);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }
}
