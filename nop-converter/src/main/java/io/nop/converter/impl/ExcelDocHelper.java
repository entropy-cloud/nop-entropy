package io.nop.converter.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.report.core.XptConstants;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.xdsl.DslModelParser;

public class ExcelDocHelper {
    public static ITemplateOutput getExcelRenderer(IDocumentObject doc, String renderType) {
        IReportEngine reportEngine = BeanContainer.getBeanByType(IReportEngine.class);
        ExcelWorkbook wk = loadExcel(doc);
        return reportEngine.getRendererForExcel(wk, renderType);
    }

    public static ExcelWorkbook loadExcel(IDocumentObject doc) {
        String fileExt = doc.getFileExt();
        if (DocConvertConstants.FILE_TYPE_XML.equals(fileExt)) {
            return (ExcelWorkbook) new DslModelParser(XptConstants.XDSL_SCHEMA_WORKBOOK).parseFromNode(doc.getNode());
        }
        if (!DocConvertConstants.FILE_TYPE_XLSX.equals(fileExt)) {
            throw new IllegalArgumentException("Document format must be xlsx");
        }
        return new ExcelWorkbookParser().parseFromResource(doc.getResource());
    }
}