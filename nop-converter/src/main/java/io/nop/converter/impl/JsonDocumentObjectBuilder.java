package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.InMemoryTextResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonDocumentObjectBuilder implements IDocumentObjectBuilder {
    private static final Logger log = LoggerFactory.getLogger(JsonDocumentObjectBuilder.class);

    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new JsonDocumentObject(fileType, resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        if (path == null)
            path = "/text/unnamed." + fileType;
        IResource resource = new InMemoryTextResource(path, text);
        return buildFromResource(fileType, resource);
    }

    static class JsonDocumentObject extends ResourceDocumentObject {
        public JsonDocumentObject(String fileType, IResource resource) {
            super(fileType, resource);
        }

        @Override
        public boolean isBinaryOnly() {
            return false;
        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            return XNode.fromValue(getModelObject(options));
        }
    }
}
