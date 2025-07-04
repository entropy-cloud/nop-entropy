package io.nop.converter;

import io.nop.converter.utils.DocConvertHelper;
import io.nop.core.resource.IResource;

public interface IDocumentObjectBuilder {
    IDocumentObject buildFromResource(String fileType, IResource resource);

    IDocumentObject buildFromText(String fileType, String path, String text);

    default boolean isBinaryOnly(String fileType) {
        return DocConvertHelper.defaultBinaryOnly(fileType);
    }
}