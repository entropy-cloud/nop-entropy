/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.excel.renderer.IReportRendererFactory;

public class XlsxReportRendererFactory implements IReportRendererFactory {

    @Override
    public IBinaryTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        ExcelTemplate template = new ExcelTemplate(model, sheetGenerator);
        return template;
    }
}
