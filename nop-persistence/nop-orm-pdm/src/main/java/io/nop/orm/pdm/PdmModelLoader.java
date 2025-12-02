package io.nop.orm.pdm;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.orm.model.OrmModel;

public class PdmModelLoader implements IResourceObjectLoader<OrmModel> {
    @Override
    public OrmModel loadObjectFromPath(String path) {
        return loadObjectFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public OrmModel loadObjectFromResource(IResource resource) {
        return new PdmModelParser().parseFromResource(resource);
    }
}
