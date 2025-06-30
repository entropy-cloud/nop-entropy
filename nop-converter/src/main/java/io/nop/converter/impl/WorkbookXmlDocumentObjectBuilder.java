package io.nop.converter.impl;

import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.excel.model.ExcelWorkbook;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_WORKBOOK_XML;

public class WorkbookXmlDocumentObjectBuilder implements IDocumentObjectBuilder {
    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new WorkbookXmlDocumentObject(resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        if (path == null)
            path = "/text/unnamed.workbook.xml";
        return buildFromResource(fileType, new InMemoryTextResource(path, text));
    }

    public static class WorkbookXmlDocumentObject extends ResourceDocumentObject {
        public WorkbookXmlDocumentObject(IResource resource) {
            super(FILE_TYPE_WORKBOOK_XML, resource);
        }

        @Override
        public ExcelWorkbook getModelObject() {
            return ExcelDocHelper.loadExcel(this);
        }

        @Override
        public String getText() {
            return getNode().xml();
        }

        @Override
        public XNode getNode() {
            return XNodeParser.instance().parseFromResource(getResource());
        }
    }
}
