package io.nop.batch.gen.model;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;

public class BatchGenModelLoader implements IResourceObjectLoader<BatchGenModel> {
    @Override
    public BatchGenModel loadObjectFromPath(String path) {
        return loadObjectFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public BatchGenModel loadObjectFromResource(IResource resource) {
        return new BatchGenModelParser().parseFromResource(resource);
    }
}
