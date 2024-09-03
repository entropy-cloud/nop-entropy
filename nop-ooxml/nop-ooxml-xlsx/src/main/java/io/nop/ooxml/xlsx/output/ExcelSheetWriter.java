/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.output;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.resource.tpl.AbstractXmlTemplate;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;

public class ExcelSheetWriter extends AbstractXmlTemplate {
    private final ExcelWriteSupport support;
    private final IExcelSheet sheet;

    public ExcelSheetWriter(IExcelSheet sheet, boolean tabSelected, int sheetIndex, ExcelWorkbook workbook) {
        support = new ExcelWriteSupport(tabSelected, sheetIndex, workbook);
        this.sheet = sheet;
    }

    public String getDrawingRelId() {
        return support.getDrawingRelId();
    }

    @Override
    public void generateXml(IXNodeHandler out, IEvalContext context) {
        support.genSheet(out, sheet, context);
    }
}