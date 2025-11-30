package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.xdef.IXDefinition;

public abstract class AbstractDslResourcePersister extends AbstractDslResourceLoader<Object>
        implements IDslResourcePersister<Object> {

    public AbstractDslResourcePersister(String schemaPath, String resolveInDir) {
        super(schemaPath, resolveInDir);
    }

    public AbstractDslResourcePersister(String schemaPath, String resolveInDir, boolean dynamic) {
        super(schemaPath, resolveInDir, dynamic);
    }

    @Override
    public void saveDslNodeToResource(IResource resource, XNode dslNode) {
        IXDefinition xdef = loadXDef();
        Object bean = DslModelHelper.dslNodeToJson(xdef, dslNode);
        saveObjectToResource(resource, bean);
    }
}
