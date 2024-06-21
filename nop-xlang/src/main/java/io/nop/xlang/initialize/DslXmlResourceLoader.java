package io.nop.xlang.initialize;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.xlang.xdsl.DslModelParser;

public class DslXmlResourceLoader implements IResourceObjectLoader<IComponentModel> {
    private final String schemaPath;
    private final String resolveInDir;

    public DslXmlResourceLoader(String schemaPath, String resolveInDir) {
        this.schemaPath = schemaPath;
        this.resolveInDir = resolveInDir;
    }

    @Override
    public IComponentModel loadObjectFromPath(String path) {
        return new DslModelParser(schemaPath).resolveInDir(resolveInDir).parseFromVirtualPath(path);
    }

    @Override
    public IComponentModel parseFromResource(IResource resource) {
        return new DslModelParser(schemaPath).resolveInDir(resolveInDir).parseFromResource(resource);
    }
}
