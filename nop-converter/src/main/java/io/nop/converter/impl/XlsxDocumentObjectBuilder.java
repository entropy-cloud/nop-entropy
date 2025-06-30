package io.nop.converter.impl;

import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;
import io.nop.report.core.XptConstants;
import io.nop.xlang.xdsl.DslModelHelper;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_XLSX;

class XlsxDocumentObjectBuilder implements IDocumentObjectBuilder {
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
        public ExcelWorkbook getModelObject() {
            return new ExcelWorkbookParser().parseFromResource(getResource());
        }

        @Override
        public String getText() {
            return getNode().xml();
        }

        @Override
        public XNode getNode() {
            ExcelWorkbook wk = getModelObject();
            XNode node = DslModelHelper.dslModelToXNode(XptConstants.XDSL_SCHEMA_WORKBOOK, wk, true);
            return node;
        }
    }
}