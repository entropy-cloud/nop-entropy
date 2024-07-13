package io.nop.core.resource.cache;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.commons.cache.ICacheManagement;
import io.nop.commons.io.serialize.IStateSerializable;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.deps.ResourceDependencySet;

public interface IResourceLoadingCache<V> extends ICacheManagement<String>, IStateSerializable, IConfigRefreshable {

    V require(String resourcePath);

    V get(String resourcePath);

    V get(String resourcePath, IResourceObjectLoader<V> loader);

    ResourceDependencySet getResourceDependsSet(String resourcePath);
}
