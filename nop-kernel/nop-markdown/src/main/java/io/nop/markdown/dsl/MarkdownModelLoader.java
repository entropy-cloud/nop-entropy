package io.nop.markdown.dsl;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;

public class MarkdownModelLoader implements IResourceObjectLoader<Object> {
    @Override
    public Object loadObjectFromPath(String path) {
        return new MarkdownObjectParser().parseFromVirtualPath(path);
    }

    @Override
    public Object parseFromResource(IResource resource) {
        return new MarkdownObjectParser().parseFromResource(resource);
    }
}
