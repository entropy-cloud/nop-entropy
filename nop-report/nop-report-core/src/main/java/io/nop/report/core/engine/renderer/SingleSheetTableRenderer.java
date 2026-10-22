/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.core.context.IEvalContext;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelTable;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.excel.renderer.IReportRendererFactory;

import java.io.IOException;
import java.io.Writer;

/**
 * 文本类渲染器（csv/fods/rtf）的公共基类：消费展开后模型的表格文本（getText()，与 html 一致）。
 * 仅取首 sheet（多 sheet 场景裁定为 follow-up）。
 */
public abstract class SingleSheetTableRenderer implements IReportRendererFactory {

    @Override
    public ITextTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        return new ITextTemplateOutput() {
            @Override
            public void generateToWriter(Writer out, IEvalContext context) throws IOException {
                if (model.getSheets() == null || model.getSheets().isEmpty())
                    return;
                generateSheet(model.getSheets().get(0).getTable(), out, context);
            }
        };
    }

    protected abstract void generateSheet(IExcelTable table, Writer out, IEvalContext context) throws IOException;
}
