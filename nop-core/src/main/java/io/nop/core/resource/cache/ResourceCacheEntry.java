/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.cache;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.lang.Null;
import io.nop.commons.util.DestroyHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * 管理缓存对象和对象的依赖集合。每次获取时如果发现依赖已经改变，则重新加载
 *
 * @param <T>
 */
public class ResourceCacheEntry<T> implements IDestroyable {
    static final Logger LOG = LoggerFactory.getLogger(ResourceCacheEntry.class);

    private final String path;
    private final ICreationListener<T> listener;

    // 值为null表示尚未加载，如果为占位对象NULL，则表示加载过，但是加载得到的结果是null
    private volatile Object object;

    // 对象记载过程中所记录的依赖资源集合
    private ResourceDependencySet deps;

    private long lastLoadTime;

    public ResourceCacheEntry(String path, ICreationListener<T> listener) {
        this.path = path;
        this.listener = listener;
    }

    public ResourceCacheEntry(String path) {
        this(path, null);
    }

    /**
     * 缓存预热的时候内部使用
     */
    ResourceCacheEntry(CacheEntryState<T> state, ICreationListener<T> listener) {
        this(state.path, listener);
        this.object = state.object;
        if (state.object != null && listener != null) {
            listener.onCreated(state.object);
        }
    }

    public static class CacheEntryState<T> implements Serializable {

        private static final long serialVersionUID = 8015662168321021834L;

        private final String path;
        private final T object;

        public CacheEntryState(String path, T object) {
            this.path = path;
            this.object = object;
        }

        public String getPath() {
            return path;
        }

        public T getObject() {
            return object;
        }

        public boolean isSerializable() {
            return object instanceof Serializable;
        }
    }

    public synchronized CacheEntryState<T> getCacheEntryState() {
        return new CacheEntryState<T>(path, getNow());
    }

    public String getPath() {
        return path;
    }

    public long getLastLoadTime() {
        return lastLoadTime;
    }

    public boolean isRefreshEnabled(int refreshMinInterval) {
        long now = CoreMetrics.currentTimeMillis();
        return now - lastLoadTime > refreshMinInterval;
    }

    /**
     * 清除当前缓存对象
     */
    public void clear() {
        synchronized (this) {
            Object oldObj = this.object;
            this.object = null;

            if (oldObj != null && oldObj != Null.NULL) {
                // 在同步块中销毁
                if (listener != null) {
                    listener.onDestroyed((T) oldObj);
                }
                DestroyHelper.safeDestroy(oldObj);
            }
        }
    }

    public ResourceDependencySet getDeps() {
        return deps;
    }

    @Override
    public void destroy() {
        this.clear();
    }

    /**
     * 获取内存中的缓存结果，如果尚未加载则直接返回null
     */
    public T getNow() {
        return normalizeObject(object);
    }

    /**
     * 检查对象是否已经发生改变，如果已经改变，则重新加载。
     *
     * @param forceRefresh 是否不进行依赖检查，直接强制重新加载
     * @return 是否重新加载
     */
    public boolean checkRefresh(boolean forceRefresh, IResourceObjectLoader<T> loader) {
        boolean refresh = forceRefresh;
        if (!refresh) {
            refresh = this.isChanged();
        }
        if (refresh) {
            reloadObject(loader);
        }
        return refresh;
    }

    private T normalizeObject(Object object) {
        return object == Null.NULL ? null : (T) object;
    }

    public boolean isChanged() {
        Object obj = object;
        if (obj instanceof IObjectChangeDetectable) {
            if (((IObjectChangeDetectable) obj).isObjectChanged())
                return true;
        }

        if (deps == null)
            return true;

        return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
    }

    public T getObject(boolean checkChanged, IResourceObjectLoader<T> loader) {
        Object value = object;
        if (value == null || checkChanged && isChanged()) {
            synchronized (this) {
                // 如果object与value不同，则表示在争抢锁的过程中已经有其他线程加载
                Object current = object;
                if (current != value && current != null) {
                    value = current;
                } else {
                    try {
                        value = loadObject(loader);
                    } catch (Exception e) {
                        // 装载失败，则原先的结果也清空，这样下次会继续执行装载
                        object = null;
                        throw NopException.adapt(e);
                    }
                    if (value != null && listener != null)
                        listener.onCreated((T) value);

                    if (value == null) {
                        object = Null.NULL;
                    } else {
                        object = value;
                    }

                }
            }
        }
        ResourceDependencySet deps = this.deps;
        if (deps != null)
            ResourceComponentManager.instance().traceAllDepends(deps.getDepends());
        return normalizeObject(value);
    }

    public void traceDepends() {
        ResourceDependencySet deps = this.deps;
        if (deps != null)
            ResourceComponentManager.instance().traceAllDepends(deps.getDepends());
    }

    private Object loadObject(IResourceObjectLoader<T> loader) {
        LOG.debug("nop.core.resource.cache-load-object:path={}", path);

        ResourceDependencySet deps = new ResourceDependencySet(path, CoreMetrics.currentTimeMillis());
        T result = ResourceComponentManager.instance().collectDependsTo(deps, () -> loader.loadObjectFromPath(path));
        this.deps = deps;
        this.lastLoadTime = CoreMetrics.currentTimeMillis();
        return result;
    }

    private synchronized void reloadObject(IResourceObjectLoader<T> loader) {
        Object oldObj = normalizeObject(this.object);

        Object obj = loadObject(loader);
        if (obj != oldObj) {
            if (listener != null) {
                listener.onCreated((T) obj);
            }
            this.object = obj;

            destroyObject(oldObj);
        }
    }

    private void destroyObject(Object oldObj) {
        if (oldObj != null && oldObj != Null.NULL) {
            DestroyHelper.safeDestroy(oldObj);
            if (listener != null) {
                listener.onDestroyed((T) oldObj);
            }
        }
    }
}