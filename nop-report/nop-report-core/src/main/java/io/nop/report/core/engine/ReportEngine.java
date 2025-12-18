/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.excel.renderer.IExcelSheetGenerator;
import io.nop.excel.renderer.IReportRendererFactory;
import io.nop.ooxml.xlsx.output.ExcelSheetBuilder;
import io.nop.ooxml.xlsx.output.ExcelTemplate;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.ooxml.xlsx.util.ExcelSheetData;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.engine.renderer.ReportRenderHelper;
import io.nop.report.core.util.ExcelReportHelper;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.nop.report.core.XptErrors.ARG_ALLOWED_FILE_TYPES;
import static io.nop.report.core.XptErrors.ARG_FILE_TYPE;
import static io.nop.report.core.XptErrors.ARG_PATH;
import static io.nop.report.core.XptErrors.ARG_RENDER_TYPE;
import static io.nop.report.core.XptErrors.ERR_XPT_UNSUPPORTED_RENDER_TYPE;
import static io.nop.report.core.XptErrors.ERR_XPT_UNSUPPORTED_XPT_FILE_TYPE;

public class ReportEngine implements IReportEngine {
    // static final Logger LOG = LoggerFactory.getLogger(ReportEngine.class);

    private Map<String, IReportRendererFactory> renderers = Collections.emptyMap();

    public void setRenderers(Map<String, IReportRendererFactory> renderers) {
        this.renderers = renderers;
    }

    @Override
    public ExcelWorkbook parseXptModelFromResource(IResource resource) {
        return new XptModelLoader().loadObjectFromResource(resource);
    }

    @Override
    public ExcelWorkbook getXptModel(String reportPath) {
        String fileType = StringHelper.fileType(reportPath);
        if (!XptConstants.ALLOWED_XPT_FILE_TYPES.contains(fileType))
            throw new NopException(ERR_XPT_UNSUPPORTED_XPT_FILE_TYPE)
                    .param(ARG_PATH, reportPath)
                    .param(ARG_FILE_TYPE, fileType)
                    .param(ARG_ALLOWED_FILE_TYPES, XptConstants.ALLOWED_XPT_FILE_TYPES);

        ExcelWorkbook model = (ExcelWorkbook) ResourceComponentManager.instance().loadComponentModel(reportPath);
        return model;
    }

    @Override
    public ExcelWorkbook generateFromXptModel(ExcelWorkbook workbook, IEvalContext ctx) {
        IExcelSheetGenerator sheetGenerator = new ExpandedSheetGenerator(workbook);
        return ReportRenderHelper.renderModel(workbook, sheetGenerator, ctx);
    }

    @Override
    public ITemplateOutput getRendererForXptModel(ExcelWorkbook model, String renderType) {
        IReportRendererFactory rendererFactory = renderers.get(renderType);
        if (rendererFactory == null)
            throw new NopException(ERR_XPT_UNSUPPORTED_RENDER_TYPE)
                    .param(ARG_RENDER_TYPE, renderType);

        return rendererFactory.buildRenderer(model, new ExpandedSheetGenerator(model));
    }

    @Override
    public ITemplateOutput getRendererForExcel(ExcelWorkbook model, String renderType) {
        IReportRendererFactory rendererFactory = renderers.get(renderType);
        if (rendererFactory == null)
            throw new NopException(ERR_XPT_UNSUPPORTED_RENDER_TYPE)
                    .param(ARG_RENDER_TYPE, renderType);

        return rendererFactory.buildRenderer(model, (ctx, action) -> model.getSheets().forEach(sheet -> action.accept(sheet, ctx)));
    }


    @Override
    public ExcelWorkbook buildXptModelFromImpModel(String impModelPath) {
        return ExcelReportHelper.buildXptModelFromImpModel(impModelPath);
    }

    @Override
    public ITemplateOutput getRendererForExcelData(Iterator<ExcelSheetData> sheetDataIterator, IResource template) {
        ExcelWorkbook tpl = new ExcelWorkbookParser().parseFromResource(template);
        ExcelSheet sheetTpl = tpl.getSheets().get(0);
        tpl.clearSheets();
        return new ExcelTemplate(tpl, new IExcelSheetGenerator() {
            @Override
            public void generate(IEvalContext context, BiConsumer<IExcelSheet, IEvalContext> consumer) {
                ExcelSheetBuilder sheetBuilder = new ExcelSheetBuilder(sheetTpl);

                while (sheetDataIterator.hasNext()) {
                    ExcelSheetData sheetData = sheetDataIterator.next();
                    IExcelSheet sheet = sheetBuilder.buildSheet(sheetData);
                    consumer.accept(sheet, context);
                }
            }
        });
    }
}