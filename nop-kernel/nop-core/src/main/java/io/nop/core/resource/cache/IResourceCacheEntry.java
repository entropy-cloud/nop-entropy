package io.nop.core.resource.cache;

import io.nop.core.resource.IResourceObjectLoader;

import java.time.Duration;

public interface IResourceCacheEntry<V> {
    boolean isRefreshEnabled(int refreshMinInterval);

    default boolean isRefreshEnabled(Duration duration) {
        if (duration == null)
            return false;
        return isRefreshEnabled((int) duration.toMillis());
    }

    V getObject(boolean checkChanged, IResourceObjectLoader<V> loader);

    void clear();

    default void clearForTenant(String tenantId){

    }
}
