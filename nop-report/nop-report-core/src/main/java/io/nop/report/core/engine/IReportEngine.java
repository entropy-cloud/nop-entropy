/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine;

import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IReportRendererRegistry;
import io.nop.ooxml.xlsx.XlsxConstants;
import io.nop.ooxml.xlsx.util.ExcelSheetData;
import io.nop.report.core.XptConstants;

import java.util.Iterator;

public interface IReportEngine extends IReportRendererRegistry {
    /**
     * 获取xpt报表模型
     */
    ExcelWorkbook getXptModel(String reportPath);

    ExcelWorkbook parseXptModelFromResource(IResource resource);

    ExcelWorkbook generateFromXptModel(ExcelWorkbook workbook, IEvalContext ctx);

    ITemplateOutput getRendererForXptModel(ExcelWorkbook workbook, String renderType);

    ITemplateOutput getRendererForExcel(ExcelWorkbook workbook, String renderType);

    default ITemplateOutput getRenderer(String reportPath, String renderType) {
        return getRendererForXptModel(getXptModel(reportPath), renderType);
    }

    default ITextTemplateOutput getHtmlRenderer(String reportPath) {
        return (ITextTemplateOutput) getRenderer(reportPath, XptConstants.RENDER_TYPE_HTML);
    }

    /**
     * 根据导入模板自动生成报表模型
     */
    ExcelWorkbook buildXptModelFromImpModel(String impModelPath);

    ITemplateOutput getRendererForExcelData(Iterator<ExcelSheetData> sheetDataIterator, IResource template);

    default ITemplateOutput getRendererForExcelData(Iterator<ExcelSheetData> sheetDataIterator) {
        IResource template = VirtualFileSystem.instance().getResource(XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH);
        return getRendererForExcelData(sheetDataIterator, template);
    }
}