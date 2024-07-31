/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.cache.ResourceCacheEntry.CacheEntryState;
import io.nop.core.resource.deps.ResourceDependencySet;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_CACHE_NULL;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_REFRESH_MIN_INTERVAL;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_RELOADABLE;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_SIZE;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_SUPPORT_SERIALIZE;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NAMED_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_NULL;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_PER_TYPE_SIZE;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_SUPPORT_SERIALIZE;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_CACHE_RETURN_NULL;

/**
 * 在LoadingCache的基础上增加文件变化检测，当资源路径对应的文件内容发生变化时，自动使得缓存失效
 *
 * @param <V>
 */
public class ResourceLoadingCache<V> implements IResourceLoadingCache<V> {
    static final SourceLocation s_loc = SourceLocation.fromClass(ResourceLoadingCache.class);
    private final String name;
    private final LocalCache<String, ResourceCacheEntry<V>> cache;
    private final IResourceObjectLoader<V> loader;
    private final ICreationListener<V> listener;

    private final IConfigReference<Integer> cacheMaxSize;
    private final IConfigReference<Boolean> checkChanged;
    private final IConfigReference<Boolean> cacheNull;
    private final IConfigReference<Duration> cacheTimeout;

    private final IConfigReference<Boolean> supportSerialize;
    private final IConfigReference<Integer> cacheRefreshMinInterval;

    public ResourceLoadingCache(String name, IResourceObjectLoader<V> loader, ICreationListener<V> listener) {
        this(name, loader, listener, null, null);
    }

    public ResourceLoadingCache(String name,
                                IResourceObjectLoader<V> loader, ICreationListener<V> listener,
                                IConfigReference<Integer> cacheMaxSize, IConfigReference<Duration> cacheTimeout) {
        this.name = name;
        this.loader = loader;
        this.listener = listener;

        this.cacheMaxSize = cacheMaxSize != null ? cacheMaxSize :
                AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_CACHE_PER_TYPE_SIZE,
                        configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_SIZE));
        this.cacheTimeout = cacheTimeout != null ? cacheTimeout :
                AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT,
                        configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_TIMEOUT));

        this.cacheNull = AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_CACHE_NULL,
                configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_CACHE_NULL));
        this.checkChanged = AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_CACHE_CHECK_CHANGED,
                configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_RELOADABLE));
        this.supportSerialize = AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_SUPPORT_SERIALIZE,
                configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_SUPPORT_SERIALIZE));


        this.cacheRefreshMinInterval = AppConfig.withOverride(s_loc, CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL,
                configVar(CFG_COMPONENT_RESOURCE_CACHE_NAMED_REFRESH_MIN_INTERVAL));
        this.cache = createCache();
    }

    @Override
    public void refreshConfig() {
        cache.getConfig().setMaximumSize(cacheMaxSize.get());
        Duration timeout = cacheTimeout.get();
        if (timeout != null)
            cache.getConfig().setExpireAfterWrite(timeout);
        cache.refreshConfig();
    }

    LocalCache<String, ResourceCacheEntry<V>> createCache() {
        CacheConfig config = new CacheConfig().useMetrics();
        config.setMaximumSize(cacheMaxSize.get());
        Duration timeout = cacheTimeout.get();
        if (timeout != null)
            config.setExpireAfterWrite(timeout);
        return LocalCache.newCache(getName(), config, this::newCacheEntry);
    }

    ResourceCacheEntry<V> newCacheEntry(String path) {
        ResourceCacheEntry<V> entry = new ResourceCacheEntry<>(path, listener);
        return entry;
    }

    String configVar(String pattern) {
        return StringHelper.renderTemplate(pattern, name -> {
            if (name.equals("name")) {
                if (getName() == null)
                    return "default";
                return getName().toLowerCase();
            }
            return null;
        });
    }

    public String toString() {
        return "ResourceLoadingCache[name=" + name + ",loader=" + loader + ",cacheNull=" + cacheNull + "]";
    }

    public boolean shouldCheckChanged() {
        return checkChanged.get();
    }

    public boolean isSupportSerialize() {
        return supportSerialize.get();
    }

    @Override
    public String getName() {
        return name;
    }

    public long estimatedSize() {
        return cache.estimatedSize();
    }

    @Override
    public CacheStats stats() {
        return cache.stats();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void remove(String key) {
        ResourceCacheEntry<V> entry = cache.getIfPresent(key);
        if (entry != null) {
            cache.remove(key, entry);
            entry.clear();
        }
    }

    public boolean checkRefresh(String key, boolean forceRefresh) {
        return checkRefresh(key, forceRefresh, loader);
    }

    public boolean checkRefresh(String key, boolean forceRefresh, IResourceObjectLoader<V> loader) {
        ResourceCacheEntry<V> entry = forceRefresh ? cache.get(key) : cache.getIfPresent(key);
        if (entry != null) {
            return entry.checkRefresh(forceRefresh, loader);
        }
        return true;
    }

    public ResourceDependencySet getResourceDependsSet(String path) {
        ResourceCacheEntry<V> entry = cache.get(path);
        if (entry == null)
            return null;
        return entry.getDeps();
    }

    public V get(String path, IResourceObjectLoader<V> loader) {
        ResourceCacheEntry<V> entry = cache.get(path);
        boolean b = checkChanged.get() && entry.isRefreshEnabled(cacheRefreshMinInterval.get());
        V value = entry.getObject(b, loader);
        if (value == null && !cacheNull.get()) {
            cache.remove(path, entry);
        }
        return value;
    }

    public V get(String path) {
        return get(path, loader);
    }

    public V require(String path) {
        return require(path, loader);
    }

    public V require(String path, IResourceObjectLoader<V> loader) {
        V v = get(path, loader);
        if (v == null)
            throw new NopException(ERR_COMPONENT_RESOURCE_CACHE_RETURN_NULL).param(ARG_RESOURCE_PATH, path);
        return v;
    }

    @Override
    public void state_saveTo(ObjectOutput out) throws IOException {
        if (!this.isSupportSerialize())
            return;

        int size = MathHelper.safeLongToInt(cache.estimatedSize());
        if (size < 0)
            size = 10;
        List<CacheEntryState<V>> states = new ArrayList<>(size);
        cache.forEachEntry((key, v) -> {
            CacheEntryState<V> state = v.getCacheEntryState();
            if (state.isSerializable())
                states.add(state);
        });
        out.writeObject(states);
    }

    @Override
    public void state_loadFrom(ObjectInput in) throws ClassNotFoundException, IOException {
        if (!isSupportSerialize())
            return;

        List<CacheEntryState<V>> states = (List<CacheEntryState<V>>) in.readObject();
        for (CacheEntryState<V> state : states) {
            ResourceCacheEntry<V> entry = new ResourceCacheEntry<V>(state, listener);
            if (!cache.putIfAbsent(state.getPath(), entry))
                entry.clear();
        }
    }
}