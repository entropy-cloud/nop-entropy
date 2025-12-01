package io.nop.xlang.xdef.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.xlang.xdsl.AbstractDslResourceLoader;
import io.nop.xlang.xdsl.DslNodeLoader;

public class XDefinitionLoader extends AbstractDslResourceLoader<Object> {
    public XDefinitionLoader() {
        super(XLangConstants.XDSL_SCHEMA_XDEF, null);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource, ResolvePhase phase) {
        return DslNodeLoader.INSTANCE.loadDslNodeFromResource(resource, this.schemaPath, phase);
    }

    @Override
    public XDefinition loadObjectFromResource(IResource resource) {
        return new XDefinitionParser().parseFromResource(resource);
    }
}
