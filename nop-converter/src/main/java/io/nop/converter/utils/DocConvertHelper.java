package io.nop.converter.utils;

import io.nop.commons.util.StringHelper;
import io.nop.converter.DocumentConverterRegistry;
import io.nop.converter.IDocumentConverter;
import io.nop.converter.IDocumentObject;
import io.nop.converter.IDocumentObjectBuilder;
import io.nop.core.resource.IResource;

public class DocConvertHelper {
    public static String convertText(String path, String text, String fromFileType, String toFileType) {
        IDocumentObjectBuilder builder = DocumentConverterRegistry.instance().requireDocumentObjectBuilder(fromFileType);
        IDocumentObject doc = builder.buildFromText(fromFileType, path, text);
        IDocumentConverter converter = DocumentConverterRegistry.instance().requireConverter(fromFileType, toFileType);
        return converter.convertToText(doc, toFileType);
    }

    public static void convertResource(IResource fromResource, IResource toResource) {
        String fromFileType = StringHelper.fileType(fromResource.getPath());
        String toFileType = StringHelper.fileType(toResource.getPath());

        convertResource(fromResource, toResource, fromFileType, toFileType);
    }

    public static void convertResource(IResource fromResource, IResource toResource, String fromFileType, String toFileType) {
        IDocumentObjectBuilder builder = DocumentConverterRegistry.instance().requireDocumentObjectBuilder(fromFileType);
        IDocumentObject doc = builder.buildFromResource(fromFileType, fromResource);
        IDocumentConverter converter = DocumentConverterRegistry.instance().requireConverter(fromFileType, toFileType);
        converter.convertToResource(doc, toFileType, toResource);
    }
}
