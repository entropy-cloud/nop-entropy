package io.nop.converter.impl;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.IDocumentObject;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IReportRendererRegistry;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.xlang.xdsl.DslModelParser;

public class ExcelDocHelper {
    public static ITemplateOutput getExcelRenderer(IDocumentObject doc, String renderType) {
        IReportRendererRegistry reportEngine = BeanContainer.getBeanByType(IReportRendererRegistry.class);
        ExcelWorkbook wk = loadExcel(doc);
        return reportEngine.getRendererForExcel(wk, renderType);
    }

    public static String renderText(IDocumentObject doc, String renderType) {
        ITextTemplateOutput renderer = (ITextTemplateOutput) getExcelRenderer(doc, renderType);
        return renderer.generateText(DisabledEvalScope.INSTANCE);
    }

    public static ExcelWorkbook loadExcel(IDocumentObject doc) {
        String fileExt = doc.getFileExt();
        if (DocConvertConstants.FILE_TYPE_XML.equals(fileExt)) {
            return (ExcelWorkbook) new DslModelParser(ExcelConstants.XDSL_SCHEMA_WORKBOOK).parseFromResource(doc.getResource());
        }
        if (!DocConvertConstants.FILE_TYPE_XLSX.equals(fileExt)) {
            throw new IllegalArgumentException("Document format must be xlsx");
        }
        return new ExcelWorkbookParser().parseFromResource(doc.getResource());
    }
}