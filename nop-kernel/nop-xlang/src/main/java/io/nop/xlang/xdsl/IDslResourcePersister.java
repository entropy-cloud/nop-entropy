package io.nop.xlang.xdsl;

import io.nop.core.resource.IResourceDslNodeSaver;
import io.nop.core.resource.IResourceObjectSaver;

public interface IDslResourcePersister<T> extends IDslResourceLoader<T>,
        IResourceObjectSaver<T>, IResourceDslNodeSaver {
}
