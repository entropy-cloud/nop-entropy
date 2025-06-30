package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.OutputStreamResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.report.core.util.ExcelReportHelper;

import java.io.IOException;
import java.io.OutputStream;

public class DslToExcelDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType) {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(doc.getFileType());

        if (config.getImpPath() != null) {
            if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType)) {
                ExcelWorkbook wk = ExcelReportHelper.generateExcelWorkbook(config.getImpPath(), doc.getModelObject());
                return ExcelHelper.toWorkbookXmlNode(wk).xml();
            }
        }
        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet");
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out) throws IOException {
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(doc.getFileType());

        if (config.getImpPath() != null) {
            if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType)) {
                ExcelWorkbook wk = ExcelReportHelper.generateExcelWorkbook(config.getImpPath(), doc.getModelObject());
                ExcelHelper.toWorkbookXmlNode(wk).saveToStream(out, null);
                return;
            } else if (DocConvertConstants.FILE_TYPE_XLSX.equals(StringHelper.lastPart(toFileType, '.'))) {
                IResource resource = new OutputStreamResource("/out.xlsx", out);
                ExcelReportHelper.saveXlsxObject(config.getImpPath(), resource, doc.getModelObject());
                return;
            }
        }
        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet: " + doc.getFileType() + " to " + toFileType);
    }
}