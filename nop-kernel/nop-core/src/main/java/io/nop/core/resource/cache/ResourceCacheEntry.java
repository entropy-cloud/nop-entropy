/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.lang.ICreationListener;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.lang.Null;
import io.nop.commons.util.DestroyHelper;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.DefaultResourceChangeChecker;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.deps.VirtualResourceDependencySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_MTIME_CHANGED;

/**
 * 管理缓存对象和对象的依赖集合。每次获取时如果发现依赖已经改变，则重新加载
 *
 * @param <T>
 */
public class ResourceCacheEntry<T> implements IDestroyable, IResourceCacheEntry<T> {
    static final Logger LOG = LoggerFactory.getLogger(ResourceCacheEntry.class);

    /**
     * 窗口二保守重载的有界重试上限：装载前后mtime持续不一致时显式失败（禁止静默固化「旧内容+新mtime」）
     */
    static final int MAX_LOAD_MTIME_RETRY = 3;

    private static final IConfigReference<Duration> CFG_CACHE_TIMEOUT = CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
    private static final IConfigReference<Integer> CFG_REFRESH_MIN_INTERVAL = CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;

    private final String path;
    private final ICreationListener<T> listener;

    // 值为null表示尚未加载，如果为占位对象NULL，则表示加载过，但是加载得到的结果是null
    private volatile Object object; //NOSONAR

    // 对象记载过程中所记录的依赖资源集合。写入发生在synchronized的loadObject内，
    // isChanged/isRefreshEnabled等读路径不持锁，需要volatile保证可见性
    private volatile ResourceDependencySet deps;

    private volatile long lastLoadTime;

    // isChanged裁决结果的记忆化状态（直连路径节流门，裁定④）：verdictCheckTime=0表示无有效记忆。
    // 读写均为良性竞争（记忆化的是纯裁决结果，并发重复walk与既有无门行为同向），因此不持锁
    private volatile long verdictCheckTime;
    private volatile boolean verdictChanged;

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

    @Override
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

    /**
     * 直连路径内建门（裁定④，checkChanged=true时由getObject调用）：与ResourceLoadingCache门路径消费同一载体、
     * 同一判据状态，节流阈值同源单键（CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL）。
     *
     * <p>门语义：
     * <ol>
     * <li>deps-空豁免：依赖收集为空的条目（text-parse类，isChanged恒false零stat）门全部旁路，行为与现状逐字节一致；</li>
     * <li>maxAge强制重载（裁定③自愈载体）：距上次装载超过TTL即强制重载，不经判据裁决（判据可能已漏检，强制绕过）；</li>
     * <li>verdict记忆化：isChanged裁决结果按entry记忆化，TTL=refresh-min-interval同源（0=关闭档=现行逐请求walk）；
     * FALSE短路返回；TRUE侧同源键重载限速：仅距lastLoadTime超过同源间隔才执行重载，否则维持现值返回；</li>
     * <li>记忆化状态在loadObject完成时重置：新装载后首查必走真实裁决。</li>
     * </ol>
     *
     * @return true表示需要重新装载
     */
    private boolean shouldReloadObject() {
        ResourceDependencySet curDeps = deps;
        if (curDeps == null)
            return true;

        Collection<ResourceDependencySet> depends = curDeps.getDepends();
        if (depends == null || depends.isEmpty()) {
            // deps-空豁免：与既有isChanged()同路径（IObjectChangeDetectable检查保留），零stat零记忆化
            return isChanged();
        }

        long now = CoreMetrics.currentTimeMillis();

        // maxAge自愈：仅对deps非空entry生效；阈值0=关闭档（确定性测试缝，裁定⑥(c)）
        Duration maxAge = CFG_CACHE_TIMEOUT.get();
        if (maxAge != null && !maxAge.isNegative() && maxAge.toMillis() > 0
                && now - lastLoadTime > maxAge.toMillis())
            return true;

        int memoTtl = CFG_REFRESH_MIN_INTERVAL.get();
        if (memoTtl > 0) {
            long checkTime = verdictCheckTime;
            if (checkTime > 0 && now - checkTime < memoTtl) {
                if (!verdictChanged)
                    return false;
                // TRUE侧同源键重载限速：仅距lastLoadTime超过同源间隔才重载
                return now - lastLoadTime > memoTtl;
            }
        }

        boolean changed = isChanged();
        if (memoTtl > 0) {
            verdictChanged = changed;
            verdictCheckTime = now;
        }
        if (!changed)
            return false;
        return now - lastLoadTime > memoTtl;
    }

    public T getObject(boolean checkChanged, IResourceObjectLoader<T> loader) {
        Object value = object;
        if (value == null || checkChanged && shouldReloadObject()) {
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

        ResourceDependencySet deps = new VirtualResourceDependencySet(path);
        Object result = executeLoadWithMtimeGuard(deps, loader);
        deps.freeze();
        this.deps = deps;
        this.lastLoadTime = CoreMetrics.currentTimeMillis();
        // 记忆化状态重置：新装载后首查必走真实裁决（裁定④门语义）
        this.verdictCheckTime = 0;
        return result;
    }

    /**
     * 窗口二消除（裁定①从属编辑点机械传递）：装载前取m1、装载完成后取m2（均经载体resolve，不走任何缓存层），
     * 经载体纯比较入口裁决；m1/m2失配=内容可能在装载期间被整体替换，保守重载（重载本身重新执行双mtime纪律）；
     * 有界重试，超限显式失败——禁止静默固化「旧内容+新mtime」，禁止以返回旧值兜底
     */
    private Object executeLoadWithMtimeGuard(ResourceDependencySet deps, IResourceObjectLoader<T> loader) {
        for (int attempt = 0; ; attempt++) {
            long m1 = resolveRootLastModified();
            Object result = ResourceComponentManager.instance().collectDependsTo(deps, () -> loader.loadObjectFromPath(path));
            long m2 = resolveRootLastModified();
            if (!DefaultResourceChangeChecker.isLoadMismatch(m1, m2))
                return result;

            if (attempt >= MAX_LOAD_MTIME_RETRY)
                throw new NopException(ERR_COMPONENT_RESOURCE_LOAD_MTIME_CHANGED).param(ARG_RESOURCE_PATH, path);

            LOG.warn("nop.core.resource.cache-load-mtime-changed:path={},attempt={}", path, attempt + 1);
        }
    }

    /**
     * 窗口二簿记采集：经载体resolve（VFS raw查找），不走任何缓存层；m1/m2同源采集，裁决经载体纯比较入口。
     * 采集禁止任何异常逃逸：entry的path不一定是可raw解析的虚拟路径（如i18n-cache以裸locale字符串为键），
     * 非正规虚拟路径直接按守卫不适用降级为常数读数（等同resource==null）——装载期一旦抛出异常，
     * NopException消息构造会重入i18n缓存查询，经可重入监视器形成同entry无限递归（StackOverflow）
     */
    private long resolveRootLastModified() {
        if (!ResourceHelper.isNormalVirtualPath(path))
            return 0;
        try {
            IResourceReference resource = DefaultResourceChangeChecker.INSTANCE.resolveResource(path);
            if (resource == null)
                return 0;
            return resource.lastModified();
        } catch (Exception e) {
            LOG.debug("nop.core.resource.cache-resolve-root-fail:path={}", path, e);
            return 0;
        }
    }

    private synchronized void reloadObject(IResourceObjectLoader<T> loader) {
        Object oldObj = normalizeObject(this.object);

        Object obj = loadObject(loader);
        if (obj != oldObj) {
            // 与getObject保持一致：null结果记录Null占位而不是裸null，且不回调onCreated(null)
            if (obj != null && listener != null) {
                listener.onCreated((T) obj);
            }
            this.object = obj == null ? Null.NULL : obj;

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