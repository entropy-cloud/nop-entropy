package io.nop.report.core.util;

import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptModelBuilder;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.ReportSheetGenerator;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.report.core.imp.ExcelTemplateToXptModelTransformer;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelHelper;

public class ExcelReportHelper extends ExcelHelper {

    public static void saveXlsxObject(String impModelPath, IResource resource, Object obj) {
        ExcelWorkbook xptModel = buildXptModelFromImpModel(impModelPath);

        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, obj);

        IBinaryTemplateOutput output = new XlsxReportRendererFactory()
                .buildRenderer(xptModel, new ReportSheetGenerator(xptModel));
        output.generateToResource(resource, scope);
    }

    public static void saveXlsxObject(IReportEngine reportEngine, String impModelPath, IResource resource,
                                      Object obj, String renderType) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, obj);

        ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel(impModelPath);
        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, renderType);
        output.generateToResource(resource, scope);
    }

    public static String getXlsxObjectAsHtml(String impModelPath, Object obj) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, obj);

        ExcelWorkbook workbook = buildXptModelFromImpModel(impModelPath);
        ITextTemplateOutput output = new HtmlReportRendererFactory().buildRenderer(workbook,new ReportSheetGenerator(workbook));
        return output.generateText(scope);
    }

    public static ExcelWorkbook buildXptModelFromImpModel(String impModelPath) {
        ImportModel impModel = (ImportModel) ResourceComponentManager.instance().loadComponentModel(impModelPath);

        IResource resource = VirtualFileSystem.instance().getResource(impModel.getTemplatePath());
        ExcelWorkbook template = new ExcelWorkbookParser().parseFromResource(resource);

        new ExcelTemplateToXptModelTransformer().transform(template, impModel);

        XLangCompileTool cp = XLang.newCompileTool().allowUnregisteredScopeVar(true);
        new XptModelBuilder(cp).build(template);

        return template;
    }

    public static void dumpXptModel(ExcelWorkbook workbook) {
        String path = workbook.resourcePath();
        if (StringHelper.isEmpty(path))
            path = "unknown.xpt.xlsx";
        path = StringHelper.removeTail(path, ".xlsx");
        if (!path.endsWith(".xpt"))
            path += ".xpt";

        path = ResourceHelper.getDumpPath(path);

        IResource resource = VirtualFileSystem.instance().getResource(path);
        DslModelHelper.saveDslModel(XptConstants.XDSL_SCHEMA_WORKBOOK, workbook, resource);
    }
}