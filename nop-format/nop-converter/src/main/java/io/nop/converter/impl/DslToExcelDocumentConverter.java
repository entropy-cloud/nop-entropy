package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocConvertConstants;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.OutputStreamResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelWorkbookGenerator;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.xlang.api.XLang;

import java.io.IOException;
import java.io.OutputStream;

public class DslToExcelDocumentConverter implements IDocumentConverter {

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(doc.getFileType());
        ComponentModelConfig.LoaderConfig loader = config.getLoader(doc.getFileType());


        if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType) && loader.getLoader() instanceof IExcelWorkbookGenerator) {
            IExcelWorkbookGenerator generator = (IExcelWorkbookGenerator) loader.getLoader();
            ExcelWorkbook wk = generator.generateWorkbook(doc.getModelObject(options), newEvalScope(options));
            return ExcelHelper.toWorkbookXmlNode(wk).xml();
        }

        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet");
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        ComponentModelConfig config = ResourceComponentManager.instance().requireModelConfigByFileType(doc.getFileType());
        ComponentModelConfig.LoaderConfig loader = config.getLoader(doc.getFileType());
        ComponentModelConfig.LoaderConfig toLoader = config.getLoader(toFileType);

        if (DocConvertConstants.FILE_TYPE_WORKBOOK_XML.equals(toFileType) && loader.getLoader() instanceof IExcelWorkbookGenerator) {
            IExcelWorkbookGenerator generator = (IExcelWorkbookGenerator) loader.getLoader();
            ExcelWorkbook wk = generator.generateWorkbook(doc.getModelObject(options), newEvalScope(options));
            ExcelHelper.toWorkbookXmlNode(wk).saveToStream(out, null);
            return;
        } else if (DocConvertConstants.FILE_TYPE_XLSX.equals(StringHelper.lastPart(toFileType, '.'))) {
            IEvalScope scope = newEvalScope(options);
            if (toLoader != null && toLoader.getLoader() instanceof IExcelWorkbookGenerator) {
                IExcelWorkbookGenerator generator = (IExcelWorkbookGenerator) toLoader.getLoader();
                ExcelWorkbook workbook = generator.generateWorkbook(doc.getModelObject(options), scope);
                ExcelHelper.saveExcel(new OutputStreamResource("/out.xlsx", out), workbook, scope);
                return;
            }
            if (toLoader != null && toLoader.getSaver() != null) {
                IResource resource = new OutputStreamResource("/out.xlsx", out);
                toLoader.getSaver().saveObjectToResource(resource, doc.getModelObject(options));
                return;
            }
        }
        throw new UnsupportedOperationException("DSL to XLSX conversion is not supported yet: " + doc.getFileType() + " to " + toFileType);
    }

    private IEvalScope newEvalScope(DocumentConvertOptions options) {
        IEvalScope scope = XLang.newEvalScope();
        Object entryTime = options == null ? null : options.getProperty(DocConvertHelper.OPTION_ZIP_ENTRY_TIME);
        if (entryTime instanceof Number) {
            scope.setLocalValue(null, OfficeConstants.VAR_ZIP_ENTRY_TIME, ((Number) entryTime).longValue());
        }
        return scope;
    }
}
