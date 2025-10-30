/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModel;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.core.engine.IReportRendererFactory;
import io.nop.report.core.engine.ReportEngine;
import io.nop.report.core.engine.renderer.HtmlReportRendererFactory;
import io.nop.report.core.engine.renderer.XlsxReportRendererFactory;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xpl.impl.XplModelParser;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenOrmHelper {

    public static void saveOrmXml(OrmModel ormModel, File outputFile) {
        IResource resource = VirtualFileSystem.instance().getResource("/nop/templates/cli/app.orm.xml.xgen");
        XplModel xpl = new XplModelParser().parseFromResource(resource);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("ormModel", ormModel);
        String text = xpl.generateText(scope);
        FileHelper.writeText(outputFile, text, null);
    }

    public static void saveOrmToExcel(OrmModel ormModel, File outputFile, boolean dump) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, XptConstants.VAR_ENTITY, ormModel);

        IReportEngine reportEngine = newReportEngine();
        ExcelWorkbook workbook = reportEngine.buildXptModelFromImpModel("/nop/orm/imp/orm.imp.xml");
        if (dump) {
            IResource modelFile = ResourceHelper.getSiblingWithExt(new FileResource(outputFile), ".xml");
            DslModelHelper.saveDslModel(XptConstants.XDSL_SCHEMA_WORKBOOK, workbook, modelFile);
        }

        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, "xlsx");
        output.generateToFile(outputFile, scope);
    }

    public static void addCatalog(ExcelWorkbook workbook, OrmModel ormModel) {
    ExcelSheet sheet = workbook.getSheet("Catalog");
        ExcelTable table = sheet.getTable();
        List<? extends IEntityModel> tables = ormModel.getEntityModels();
        int index = 1;
        String styleId0 = table.getCell(1, 0).getStyleId();
        String styleId = table.getCell(1, 1).getStyleId();
        String styleId2 = table.getCell(1, 2).getStyleId();
        String styleId3 = table.getCell(1,3).getStyleId();

        for (IEntityModel entityModel : tables) {
            ExcelRow row = table.makeRow(index++);
            row.makeCell(0).setValue(index);
            ((ExcelCell) row.makeCell(0)).setStyleId(styleId0);

            ExcelCell cell = (ExcelCell) row.makeCell(1);
            cell.setValue(entityModel.getTableName());
            cell.setLinkUrl("ref:" + entityModel.getTableName() + "!A1");
            cell.setStyleId(styleId);

            ExcelCell cell2 = (ExcelCell) row.makeCell(2);
            cell2.setStyleId(styleId2);
            cell2.setValue(entityModel.getDisplayName());

            ExcelCell cell3 = (ExcelCell) row.makeCell(3);
            cell3.setStyleId(styleId3);
            cell3.setValue(entityModel.getComment());
        }
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
