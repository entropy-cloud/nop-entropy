package io.nop.converter;

import io.nop.core.resource.IResource;

public interface IDocumentObjectBuilder {
    IDocumentObject buildFromResource(String fileType, IResource resource);

    IDocumentObject buildFromText(String fileType, String path, String text);
}