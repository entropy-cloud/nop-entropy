package io.nop.xlang.xmeta.xjava;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdsl.AbstractDslResourceLoader;
import io.nop.xlang.xmeta.IObjMeta;

public class JavaObjMetaLoader extends AbstractDslResourceLoader<Object> {
    public JavaObjMetaLoader() {
        super(XLangConstants.XDSL_SCHEMA_XMETA, null, false);
    }

    @Override
    public IObjMeta loadObjectFromResource(IResource resource) {
        return new JavaObjMetaParser().parseFromResource(resource);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource) {
        IObjMeta objMeta = loadObjectFromResource(resource);
        return transformBeanToNode(objMeta);
    }
}
