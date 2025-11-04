package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.IResourceParser;

public class SimpleDslParser implements IResourceParser<XNode> {

    public XNode parseFromResource(IResource resource, boolean ignoreUnknown) {
        return XNodeParser.instance().parseFromResource(resource, ignoreUnknown);
    }
}
