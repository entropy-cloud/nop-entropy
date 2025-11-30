package io.nop.core.resource;

import io.nop.core.lang.xml.XNode;

public interface IResourceDslNodeLoader {
    XNode loadDslNodeFromResource(IResource resource);
}
