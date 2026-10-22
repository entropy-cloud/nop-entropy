/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.report.core.XptConstants;

import java.io.File;

/**
 * xlsx 渲染器。包装输出以在生成入口写入当前渲染类型（子报表等需要感知渲染形态的机制消费）。
 */
public class XlsxReportRendererFactory implements IReportRendererFactory {

    @Override
    public IBinaryTemplateOutput buildRenderer(ExcelWorkbook model, IExcelSheetGenerator sheetGenerator) {
        ExcelTemplate template = new ExcelTemplate(model, sheetGenerator);
        return new IBinaryTemplateOutput() {

            private void markRenderType(IEvalContext context) {
                context.getEvalScope().setLocalValue(null, XptConstants.VAR_XPT_RENDER_TYPE,
                        XptConstants.RENDER_TYPE_XLSX);
            }

            @Override
            public void generateToStream(java.io.OutputStream out, IEvalContext context) {
                markRenderType(context);
                try {
                    template.generateToStream(out, context);
                } catch (Exception e) {
                    throw NopException.adapt(e);
                }
            }

            @Override
            public void generateToFile(File file, IEvalContext context) {
                markRenderType(context);
                try {
                    template.generateToFile(file, context);
                } catch (Exception e) {
                    throw NopException.adapt(e);
                }
            }

            @Override
            public void generateToResource(IResource resource, IEvalContext context) {
                markRenderType(context);
                template.generateToResource(resource, context);
            }
        };
    }
}
