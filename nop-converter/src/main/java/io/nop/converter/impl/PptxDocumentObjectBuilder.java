package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.resource.IResource;
import io.nop.ooxml.markdown.PptxToMarkdownConverter;

import static io.nop.converter.DocConvertConstants.FILE_TYPE_PPTX;

public class PptxDocumentObjectBuilder implements IDocumentObjectBuilder {
    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new PptxDocumentObject(resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        throw new UnsupportedOperationException();
    }

    public static class PptxDocumentObject extends ResourceDocumentObject {
        public PptxDocumentObject(IResource resource) {
            super(FILE_TYPE_PPTX, resource);
        }

        @Override
        public Object getModelObject(DocumentConvertOptions options) {
            return new PptxToMarkdownConverter().convertFromResource(getResource());
        }

    }
}
