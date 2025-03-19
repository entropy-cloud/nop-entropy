package io.nop.batch.core;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceLocator;
import io.nop.core.resource.impl.InMemoryTextResource;

public class DebugResourceLocator implements IResourceLocator {
    @Override
    public IResource getResource(String path) {
        return new InMemoryTextResource(path, "");
    }
}
