package io.nop.xlang.xdsl;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;

public interface IDslResourceObjectLoader<T> extends IResourceObjectLoader<T> {
    XNode parseNodeFromResource(IResource resource);
}
