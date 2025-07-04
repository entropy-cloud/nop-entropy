package io.nop.converter;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;

import java.io.IOException;
import java.io.OutputStream;

public interface IDocumentObject extends ISourceLocationGetter {
    String getFileType();

    default String getFileExt() {
        return StringHelper.lastPart(getFileType(), '.');
    }

    boolean isBinaryOnly();

    Object getModelObject(DocumentConvertOptions options);

    String getText(DocumentConvertOptions options);

    default XNode getNode(DocumentConvertOptions options) {
        return XNodeParser.instance().parseFromText(getLocation(), getText(options));
    }

    IResource getResource();

    void saveToResource(IResource resource, DocumentConvertOptions options);

    void saveToStream(OutputStream out, DocumentConvertOptions options) throws IOException;
}