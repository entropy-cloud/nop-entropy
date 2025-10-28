package io.nop.converter.impl;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConvertOptions;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ChainedDocumentConverter implements IDocumentConverter {
    private final IDocumentObjectBuilder docObjBuilder;
    private final IDocumentConverter firstConverter;
    private final IDocumentConverter secondConverter;
    private final String intermediateType;

    public ChainedDocumentConverter(IDocumentObjectBuilder docObjBuilder, IDocumentConverter firstConverter,
                                    IDocumentConverter secondConverter,
                                    String intermediateType) {
        this.docObjBuilder = docObjBuilder;
        this.firstConverter = firstConverter;
        this.secondConverter = secondConverter;
        this.intermediateType = intermediateType;
    }

    @Override
    public String convertToText(IDocumentObject doc, String toFileType, DocumentConvertOptions options) {
        // Convert to intermediate type first
        String intermediateResult = firstConverter.convertToText(doc, intermediateType, options);
        String path = "/temp/" + StringHelper.replaceFileType(doc.resourcePath(), intermediateType);
        // Create a document object from the intermediate result
        IDocumentObject intermediateDoc = docObjBuilder.buildFromText(intermediateType, path, intermediateResult);
        // Then convert to final type
        return secondConverter.convertToText(intermediateDoc, toFileType, options);
    }

    @Override
    public void convertToStream(IDocumentObject doc, String toFileType, OutputStream out, DocumentConvertOptions options) throws IOException {
        // Use ByteArrayOutputStream to collect intermediate result
        try (ByteArrayOutputStream intermediateOut = new ByteArrayOutputStream()) {
            firstConverter.convertToStream(doc, intermediateType, intermediateOut, options);

            // Create byte array resource from the intermediate result
            byte[] intermediateBytes = intermediateOut.toByteArray();
            String path = "/temp/" + StringHelper.replaceFileType(StringHelper.fileFullName(doc.resourcePath()), intermediateType);
            IResource intermediateResource = new ByteArrayResource(path, intermediateBytes, -1L);

            // Create a document object from the intermediate resource
            IDocumentObject intermediateDoc = docObjBuilder.buildFromResource(intermediateType, intermediateResource);

            // Then convert to final type
            secondConverter.convertToStream(intermediateDoc, toFileType, out, options);
        }
    }
}