package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;

public class SimpleDslLoader extends AbstractDslResourceLoader<Object> {

    public SimpleDslLoader() {
        super("/simple/simple.xdef", null);
    }

    @Override
    public XNode loadDslNodeFromResource(IResource resource) {
        return XNodeParser.instance().parseFromResource(resource);
    }
}
