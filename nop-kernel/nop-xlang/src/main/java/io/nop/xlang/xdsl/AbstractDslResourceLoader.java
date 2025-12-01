package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;

public abstract class AbstractDslResourceLoader<T> implements IDslResourceLoader<T> {
    protected final String schemaPath;
    protected final String resolveInDir;
    protected final boolean dynamic;

    public AbstractDslResourceLoader(String schemaPath, String resolveInDir, boolean dynamic) {
        this.schemaPath = schemaPath;
        this.resolveInDir = resolveInDir;
        this.dynamic = dynamic;
    }

    public AbstractDslResourceLoader(String schemaPath, String resolveInDir) {
        this(schemaPath, resolveInDir, false);
    }

    public AbstractDslResourceLoader(String schemaPath) {
        this(schemaPath, null);
    }

    protected IXDefinition loadXDef() {
        return SchemaLoader.loadXDefinition(schemaPath);
    }

    protected XNode transformBeanToNode(Object bean) {
        return DslModelHelper.dslModelToXNode(schemaPath, bean);
    }

    protected XNode loadXmlFile(IResource resource, ResolvePhase phase) {
        return DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource, schemaPath, phase);
    }

    @Override
    public T loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadObjectFromResource(resource);
    }

    @Override
    public T loadObjectFromResource(IResource resource) {
        XNode node = loadDslNodeFromResource(resource, ResolvePhase.raw);
        return (T) new DslModelParser(schemaPath).resolveInDir(resolveInDir).dynamic(dynamic).parseFromNode(node);
    }
}
