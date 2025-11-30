package io.nop.markdown.simple;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.markdown.model.MarkdownDocument;

public class MarkdownDocumentLoader implements IResourceObjectLoader<MarkdownDocument> {
    @Override
    public MarkdownDocument loadObjectFromPath(String path) {
        return loadObjectFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public MarkdownDocument loadObjectFromResource(IResource resource) {
        return new MarkdownDocumentParser().parseFromResource(resource);
    }
}
