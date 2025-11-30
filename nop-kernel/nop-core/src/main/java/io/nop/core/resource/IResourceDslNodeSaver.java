package io.nop.core.resource;

import io.nop.core.lang.xml.XNode;

public interface IResourceDslNodeSaver {
    void saveDslNodeToResource(IResource resource, XNode dslNode);
}
