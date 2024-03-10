/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.orm.model.OrmModel;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class GenOrmHelper {

    public static void saveOrmToExcel(OrmModel ormModel, File outputFile, boolean dump) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/orm/imp/orm.imp.xml");
        if (dump) {
            IResource modelFile = ResourceHelper.getSiblingWithExt(new FileResource(outputFile), ".xpt");
            DslModelHelper.saveDslModel(XptConstants.XDSL_SCHEMA_WORKBOOK, workbook, modelFile);
        }

        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
        output.generateToFile(outputFile, scope);
    }

    private static IReportEngine newReportEngine() {
        ReportEngine reportEngine = new ReportEngine();
        Map<String, IReportRendererFactory> renderers = new HashMap<>();
        renderers.put(XptConstants.RENDER_TYPE_XLSX, new XlsxReportRendererFactory());
        renderers.put(XptConstants.RENDER_TYPE_HTML, new HtmlReportRendererFactory());
        reportEngine.setRenderers(renderers);
        return reportEngine;
    }
}
