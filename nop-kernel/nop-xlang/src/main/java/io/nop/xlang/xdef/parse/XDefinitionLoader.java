package io.nop.xlang.xdef.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.feature.XModelInclude;
import io.nop.xlang.xdef.impl.XDefinition;
import io.nop.xlang.xdsl.AbstractDslResourceLoader;

public class XDefinitionLoader extends AbstractDslResourceLoader<Object> {
    public XDefinitionLoader() {
        super(XLangConstants.XDSL_SCHEMA_XDEF, null);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource) {
        return XModelInclude.instance().loadActiveNodeFromResource(resource);
    }

    @Override
    public XDefinition loadObjectFromResource(IResource resource) {
        return new XDefinitionParser().parseFromResource(resource);
    }
}
