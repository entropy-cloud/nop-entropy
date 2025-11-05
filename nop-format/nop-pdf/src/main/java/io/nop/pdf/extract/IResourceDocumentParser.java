package io.nop.pdf.extract;

import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.core.resource.IResource;

public interface IResourceDocumentParser {
    ResourceDocument parseFromResource(IResource resource);

    void addPostProcessor(IResourceDocumentProcessor processor);
}