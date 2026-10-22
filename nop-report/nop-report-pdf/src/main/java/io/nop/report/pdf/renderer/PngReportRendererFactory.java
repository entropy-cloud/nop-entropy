/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.renderer;

import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.excel.renderer.IReportRendererFactory;

/**
 * png renderType：报表渲染为 PDF 后 PDFBox 逐页转 PNG（zip 输出）。
 */
public class PngReportRendererFactory implements IReportRendererFactory {

    @Override
    public IBinaryTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new PngReportRenderer(model, sheetGenerator);
    }
}
