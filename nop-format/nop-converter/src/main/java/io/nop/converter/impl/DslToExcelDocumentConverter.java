package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.OutputStreamResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelWorkbookGenerator;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.xlang.api.XLang;

import java.io.IOException;
import java.io.OutputStream;

public class DslToExcelDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(doc.getFileType());
        ComponentModelConfig.LoaderConfig loader = config.getLoader(doc.getFileType());

        if (config.getImpPath() != null) {
            if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType) && loader instanceof IExcelWorkbookGenerator) {
                IExcelWorkbookGenerator generator = (IExcelWorkbookGenerator) loader;
                ExcelWorkbook wk = generator.generateWorkbook(doc.getModelObject(options), XLang.newEvalScope());
                return ExcelHelper.toWorkbookXmlNode(wk).xml();
            }
        }
        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet");
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(doc.getFileType());
        ComponentModelConfig.LoaderConfig loader = config.getLoader(doc.getFileType());

        if (config.getImpPath() != null) {
            if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType) && loader instanceof IExcelWorkbookGenerator) {
                IExcelWorkbookGenerator generator = (IExcelWorkbookGenerator) loader;
                ExcelWorkbook wk = generator.generateWorkbook(doc.getModelObject(options), XLang.newEvalScope());
                ExcelHelper.toWorkbookXmlNode(wk).saveToStream(out, null);
                return;
            } else if (DocConvertConstants.FILE_TYPE_XLSX.equals(StringHelper.lastPart(toFileType, '.'))) {
                IResource resource = new OutputStreamResource("/out.xlsx", out);
                ComponentModelConfig.LoaderConfig loaderConfig = config.getLoader(toFileType);
                if (loaderConfig != null && loaderConfig.getSaver() != null) {
                    loaderConfig.getSaver().saveObjectToResource(resource, doc.getModelObject(options));
                    return;
                }
            }
        }
        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet: " + doc.getFileType() + " to " + toFileType);
    }
}