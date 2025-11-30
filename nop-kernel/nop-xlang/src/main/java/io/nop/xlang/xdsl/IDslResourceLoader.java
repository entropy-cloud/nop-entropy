package io.nop.xlang.xdsl;

import io.nop.core.resource.IResourceDslNodeLoader;
import io.nop.core.resource.IResourceObjectLoader;

public interface IDslResourceLoader<T> extends
        IResourceObjectLoader<T>, IResourceDslNodeLoader {
}