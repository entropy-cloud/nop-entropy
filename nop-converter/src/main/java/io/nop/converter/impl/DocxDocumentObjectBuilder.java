package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.resource.IResource;
import io.nop.ooxml.markdown.DocxToMarkdownConverter;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_DOCX;

public class DocxDocumentObjectBuilder implements IDocumentObjectBuilder {
    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new DocxDocumentObject(resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        throw new UnsupportedOperationException();
    }

    public static class DocxDocumentObject extends ResourceDocumentObject {
        public DocxDocumentObject(IResource resource) {
            super(FILE_TYPE_DOCX, resource);
        }

        @Override
        public Object getModelObject(DocumentConvertOptions options) {
            return new DocxToMarkdownConverter().convertFromResource(getResource());
        }

    }
}
