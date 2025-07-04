package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.InMemoryTextResource;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class XmlDocumentObjectBuilder implements IDocumentObjectBuilder {

    @Override
    public IDocumentObject buildFromResource(String fileType, IResource resource) {
        return new XmlDocumentObject(fileType, resource);
    }

    @Override
    public IDocumentObject buildFromText(String fileType, String path, String text) {
        if (path == null)
            path = "/text/unnamed." + fileType;
        return buildFromResource(fileType, new InMemoryTextResource(path, text));
    }

    public static class XmlDocumentObject extends ResourceDocumentObject {
        public XmlDocumentObject(String fileType, IResource resource) {
            super(fileType, resource);
        }

        @Override
        public Object getModelObject(DocumentConvertOptions options) {
            return getNode(options).toJsonObject();
        }

        @Override
        public void saveToResource(IResource resource, DocumentConvertOptions options) {
            ResourceHelper.writeText(resource, getText(options));
        }

        @Override
        public void saveToStream(OutputStream out, DocumentConvertOptions options) throws IOException {
            out.write(getText(options).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String getText(DocumentConvertOptions options) {
            return getNode(options).xml();
        }

        @Override
        public XNode getNode(DocumentConvertOptions options) {
            return XNodeParser.instance().parseFromResource(getResource());
        }
    }
}