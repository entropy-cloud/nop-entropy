package io.nop.xlang.initialize;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.xdsl.AbstractDslResourcePersister;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslNodeLoader;

public class DslXmlResourceLoader extends AbstractDslResourcePersister {
    public DslXmlResourceLoader(String schemaPath, String resolveInDir) {
        super(schemaPath, resolveInDir);
    }

    public DslXmlResourceLoader(String schemaPath, String resolveInDir, boolean dynamic) {
        super(schemaPath, resolveInDir, dynamic);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        return DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource, schemaPath, phase);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        XNode node = obj instanceof XNode ? (XNode) obj : DslModelHelper.dslModelToXNode(schemaPath, obj);
        node.saveToResource(resource, null);
    }
}
