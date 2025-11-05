package io.nop.converter.impl;

import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public interface ITextDocumentConverter extends IDocumentConverter {


    @Override
    default void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        String text = convertToText(doc, toFileType, options);
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    default void convertToResource(IDocumentObject doc, String toFileType, IResource resource, DocumentConvertOptions options) {
        String text = convertToText(doc, toFileType, options);
        ResourceHelper.writeText(resource, text, null);
    }
}
