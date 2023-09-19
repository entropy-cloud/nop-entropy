/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.cache;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.nop.commons.CommonErrors.ARG_CACHE_NAME;
import static io.nop.commons.CommonErrors.ERR_CACHE_DUPLICATE_REGISTRATION;

/**
 * 统一管理所有全局缓存
 */
@GlobalInstance
public class GlobalCacheRegistry {
    static final Logger LOG = LoggerFactory.getLogger(GlobalCacheRegistry.class);

    static final GlobalCacheRegistry _instance = new GlobalCacheRegistry();

    public static GlobalCacheRegistry instance() {
        return _instance;
    }

    private final ConcurrentMap<String, ICacheManagement<?>> caches = new ConcurrentHashMap<>();

    public void removeCacheEntry(@Nonnull CacheRef ref) {
        ICacheManagement<?> cache = caches.get(ref.getCacheName());
        if (cache != null) {
            Object cacheKey = ref.getCacheKey();
            if (cacheKey == null) {
                cache.clear();
            } else {
                ((ICacheManagement) cache).remove(cacheKey);
            }
        }
    }

    public void clearForTenant(String tenantId) {
        for (ICacheManagement<?> cache : caches.values()) {
            cache.clearForTenant(tenantId);
        }
    }

    public void clearAllCache() {
        for (ICacheManagement<?> cache : caches.values()) {
            cache.clear();
        }
    }

    public ICacheManagement<?> getCache(@Nonnull String name) {
        return caches.get(name);
    }

    public void register(@Nonnull ICacheManagement<?> cache) {
        ICacheManagement<?> oldCache = caches.put(cache.getName(), cache);
        if (oldCache != null)
            throw new NopException(ERR_CACHE_DUPLICATE_REGISTRATION).param(ARG_CACHE_NAME, cache.getName());

        LOG.debug("nop.commons.cache.register-cache:name={}", cache.getName());
    }

    public void unregister(ICacheManagement<?> cache) {
        caches.remove(cache.getName(), cache);
    }
    //
    // /**
    // * 删除硬盘上保存的缓存数据文件
    // *
    // * @return
    // */
    // public void deleteCacheData() {
    // for (String name : this.caches.keySet()) {
    // getCacheDataFile(name).delete();
    // }
    // }
    //
    // /**
    // * 将内存中的缓存数据转储到数据文件中
    // */
    // public void saveCacheData() {
    // for (ICacheManagement<?> cache : caches.values()) {
    // saveCacheData(cache);
    // }
    // }
    //
    // /**
    // * 从缓存数据文件中加载数据
    // */
    // public void loadCacheData() {
    // for (ICacheManagement<?> cache : caches.values()) {
    // loadCacheData(cache);
    // }
    // }
    //
    // IResource getCacheDataFile(String name) {
    // // 不同的app对应不同的缓存存储文件
    // String path = "/_store/cache/" + AppConfig.appName() + "/" + name + ".data";
    // return VirtualFileSystem.instance().getResource(path);
    // }
    //
    // void saveCacheData(ICacheManagement<?> cache) {
    // if (cache instanceof IStateSerializable) {
    // IResource resource = getCacheDataFile(cache.getName());
    // saveCacheDataTo(cache.getName(), (IStateSerializable) cache, resource);
    // }
    // }
    //
    // void loadCacheData(ICacheManagement<?> cache) {
    // // 单元测试可能没有初始化GlobalResourceStore
    // if (VirtualFileSystem.instance() == null)
    // return;
    //
    // if (cache instanceof IStateSerializable) {
    // IResource resource = getCacheDataFile(cache.getName());
    // if (resource.exists())
    // loadCacheDataFrom(cache.getName(), (IStateSerializable) cache, resource);
    // }
    // }
    //
    // /**
    // * 将内存中的缓存数据转储到数据文件中
    // */
    // void saveCacheDataTo(String name, IStateSerializable cache, IResource resource) {
    // try {
    // LOG.info("nop.core.resource.cache.begin-save-state-for:name={}", name);
    // ResourceHelper.writeState(resource, cache);
    // } catch (Exception e) {
    // LOG.error("nop.core.resource.cache.save-state-fail:name={}", name, e);
    // throw NopException.adapt(e);
    // }
    // }
    //
    // void loadCacheDataFrom(String name, IStateSerializable cache, IResource resource) {
    // try {
    // LOG.info("nop.core.resource.cache.begin-save-state-for:name={}", name);
    // ResourceHelper.readState(resource, cache);
    // } catch (Exception e) {
    // LOG.error("nop.core.resource.cache.save-state-fail:name={}", name, e);
    // // 这里不抛出异常，装载缓存数据失败不是致命的错误
    // }
    // }
}