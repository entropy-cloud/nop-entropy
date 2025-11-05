package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.ooxml.xlsx.util.ExcelHelper;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_XLSX;

public class XlsxDocumentObjectBuilder implements IDocumentObjectBuilder {
    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new XlsxDocumentObject(resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        throw new UnsupportedOperationException("buildFromText");
    }

    public static class XlsxDocumentObject extends ResourceDocumentObject {
        public XlsxDocumentObject(IResource resource) {
            super(FILE_TYPE_XLSX, resource);
        }

        @Override
        public ExcelWorkbook getModelObject(DocumentConvertOptions options) {
            return new ExcelWorkbookParser().parseFromResource(getResource());
        }

        @Override
        public String getText(DocumentConvertOptions options) {
            return getNode(options).xml();
        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            ExcelWorkbook wk = getModelObject(options);
            XNode node = ExcelHelper.toWorkbookXmlNode(wk);
            return node;
        }
    }
}