package io.nop.xlang.initialize;

import io.nop.api.core.util.IComponentModel;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdsl.DslModelParser;
import io.nop.xlang.xdsl.IDslResourceObjectLoader;

public class DslXmlResourceLoader implements IDslResourceObjectLoader<IComponentModel> {
    private final String schemaPath;
    private final String resolveInDir;
    private boolean dynamic;

    public DslXmlResourceLoader(String schemaPath, String resolveInDir) {
        this.schemaPath = schemaPath;
        this.resolveInDir = resolveInDir;
    }

    public DslXmlResourceLoader dynamic(boolean b) {
        this.dynamic = b;
        return this;
    }

    @Override
    public IComponentModel loadObjectFromPath(String path) {
        return parseFromResource(VirtualFileSystem.instance().getResource(path));
    }

    @Override
    public XNode parseNodeFromResource(IResource resource) {
        return new DslModelParser(schemaPath).resolveInDir(resolveInDir).dynamic(dynamic).parseNodeFromResource(resource, false);
    }

    @Override
    public IComponentModel parseFromResource(IResource resource) {
        return new DslModelParser(schemaPath).resolveInDir(resolveInDir).dynamic(dynamic).parseFromResource(resource);
    }
}
