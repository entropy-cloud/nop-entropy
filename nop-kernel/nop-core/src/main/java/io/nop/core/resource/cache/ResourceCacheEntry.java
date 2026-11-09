/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.cache;

import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.resource.IResourceReference;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.ResourceRetirementQueue;
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
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT;
import static io.nop.core.CoreConfigs.CFG_COMPONENT_RESOURCE_REFRESH_MIN_INTERVAL;
import static io.nop.core.CoreErrors.ARG_DEP_STACK;
import static io.nop.core.CoreErrors.ARG_RESOURCE_PATH;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_CIRCULAR_WAIT;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_MTIME_CHANGED;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_REENTRANT_LOOP;
import static io.nop.core.CoreErrors.ERR_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT;

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

    // 装载状态机（plan 2504 裁定②，C2-001）：IDLE=空闲；LOADING=已安装占位、装载体在监视器外执行。
    // 读写均在entry监视器内（等待者在wait前登记等待图边），非volatile安全
    static final int STATE_IDLE = 0;
    static final int STATE_LOADING = 1;
    private int loadState = STATE_IDLE;
    private Thread loaderThread;

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
     * 清除当前缓存对象（失效标记 + 延迟销毁，plan 2504 裁定③，C2-002）：
     * 监视器内只做 this.object = null 标记失效；旧对象经退役队列延迟销毁（销毁动作 =
     * listener.onDestroyed + DestroyHelper.safeDestroy，排空期在任意 entry 监视器外执行）——
     * 快路径无锁交出的引用在宽限期内保持可用，use-after-destroy 窗消除为有界宽限。
     * 失效入口（ResourceLoadingCache.remove / state_loadFrom putIfAbsent 失败 / destroy()）均经本方法
     * 到达同一延迟销毁路径；onDestroyed 相互保序（退役队列 FIFO）。
     */
    public void clear() {
        Object oldObj;
        synchronized (this) {
            oldObj = this.object;
            this.object = null;
        }
        // C7-008（plan 2548 执行期勘误 E-2548-2）：**不在 entry.clear 淘汰依赖集**——
        // dependencyMap 的条目是「资源 → 依赖它的集合」，条目失效≠驱逐（其它条目可能仍依赖该集
        // 感知变更）；实测删除会断开 TestResourceCacheInvalidationChain 的失效重载链（3 red）。
        // 逐条淘汰经 ResourceDependsManager.removeDependency 供显式清理路径使用，entry 生命周期
        // 内不自动淘汰（容量告警仍在，单调增长风险由告警观测）。
        retireDestroyedObject(oldObj);
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
            value = doGetObject(value, loader);
        }
        ResourceDependencySet deps = this.deps;
        if (deps != null)
            ResourceComponentManager.instance().traceAllDepends(deps.getDepends());
        return normalizeObject(value);
    }

    /**
     * 装载状态机编排（plan 2504 裁定②，C2-001）：监视器内只做同 key 去重 + 占位安装 + 发布；
     * 装载体（解析+编译 I/O）一律在监视器外执行。跨线程装载/编译等待统一登记于
     * {@link LoadWaitGraph}（机制单点，LazyFn 编译等待共用）做环检测；检测竞态漏检窗由
     * 有界等待兜底（CFG_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT，≤0=不设上界）。
     */
    private Object doGetObject(Object value, IResourceObjectLoader<T> loader) {
        synchronized (this) {
            for (;;) {
                Object current = this.object;
                if (current != null && current != value) {
                    // 同 key 去重：争抢/等待期间其他线程已发布新结果
                    return current;
                }
                if (loadState == STATE_LOADING) {
                    // 环错误消息构造重入降级：NopException 消息解析会重入 i18n 缓存装载（I18nMessageManager →
                    // ResourceLoadingCache）。若在环错误构造期间再次命中装载占位，按基线重入语义直接重载
                    // （不等待、不构造新错误——flag 已置位压制装载期错误构造的递归），阻断
                    // 「构造错误 → i18n 装载 → 构造错误」无限递归（SOE 面）；仅发生在错误构造栈内
                    if (IN_RING_ERROR_CONSTRUCTION.get() == Boolean.TRUE) {
                        if (LOG.isDebugEnabled())
                            LOG.debug("nop.core.resource.cache-ring-error-reentry:path={}", path);
                        return loadObject(loader);
                    }
                    // 占位命中：等待协议（监视器外装载完成/失败复位时 notifyAll）
                    Object waited = awaitLoadCompletion(value);
                    if (waited != null)
                        return waited;
                    // 失败复位/空闲 → 本线程成为装载者（失败不驻留，禁 C2-015 式条目驻留）
                    continue;
                }
                // 占位安装（监视器内），装载体在监视器外执行
                loadState = STATE_LOADING;
                loaderThread = Thread.currentThread();
                break;
            }
        }

        Object result;
        try {
            result = loadObject(loader);
        } catch (Exception e) {
            synchronized (this) {
                // 装载失败：清除陈旧结果并复位占位（与既有 getObject 失败语义一致），唤醒等待者各自重试
                this.object = null;
                finishLoading();
            }
            throw NopException.adapt(e);
        } catch (Throwable e) {
            synchronized (this) {
                // Error 不落占位悬挂：复位后原样上抛
                this.object = null;
                finishLoading();
            }
            throw e;
        }

        synchronized (this) {
            if (result != null && listener != null)
                listener.onCreated((T) result);

            if (result == null) {
                object = Null.NULL;
            } else {
                object = result;
            }
            finishLoading();
        }
        return result;
    }

    /**
     * 等待协议（调用时持 entry 监视器）：先做同线程重入环检测（SOE 面闭合），再登记等待图边做
     * 跨线程环检测，最后进入有界等待。返回非 null = 已发布结果（去重复用）；返回 null = 装载失败
     * 复位/空闲，调用方成为装载者。环命中/等待超时抛类型化错误并即时打断（无永久悬挂路径）。
     */
    private Object awaitLoadCompletion(Object value) {
        Thread current = Thread.currentThread();
        if (loaderThread == current) {
            // 同线程重入环（loadDslModel 循环依赖的同线程形态）：类型化收敛，SOE 面闭合
            throw ringError(ERR_COMPONENT_RESOURCE_LOAD_REENTRANT_LOOP, null);
        }

        Thread target = loaderThread;
        long timeoutMillis = loadWaitTimeoutMillis();
        long deadline = timeoutMillis > 0 ? CoreMetrics.currentTimeMillis() + timeoutMillis : 0L;

        // 跨线程环检测：登记「等待者 → 目标装载线程」边并回溯目标等待链（与 LazyFn 编译等待共用同一等待图）
        LoadWaitGraph.register(current, target);
        try {
            if (LoadWaitGraph.wouldCycle(current, target)) {
                throw ringError(ERR_COMPONENT_RESOURCE_LOAD_CIRCULAR_WAIT, LoadWaitGraph.describeChain(current))
                        .param(ARG_RESOURCE_PATH, path);
            }
            for (;;) {
                Object currentObj = this.object;
                if (currentObj != null && currentObj != value) {
                    // 装载完成发布：去重复用
                    return currentObj;
                }
                if (loadState != STATE_LOADING) {
                    // 失败复位/空闲：交还调用方重新裁决
                    return null;
                }
                if (deadline > 0) {
                    long remaining = deadline - CoreMetrics.currentTimeMillis();
                    if (remaining <= 0) {
                        // 有界兜底：等待图回溯存在竞态漏检窗，超时类型化（携带等待链快照，诊断可见）
                        throw ringError(ERR_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT,
                                LoadWaitGraph.describeChain(current)).param(ARG_RESOURCE_PATH, path);
                    }
                    wait(remaining);
                } else {
                    wait();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } finally {
            LoadWaitGraph.unregister(current);
        }
    }

    private void finishLoading() {
        loadState = STATE_IDLE;
        loaderThread = null;
        notifyAll();
    }

    /**
     * 有界等待兜底上界（毫秒）：装载等待与 LazyFn 编译等待共用同一配置键
     * （CFG_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT），≤0 = 不设上界。
     */
    public static long loadWaitTimeoutMillis() {
        Integer timeout = CFG_COMPONENT_RESOURCE_LOAD_WAIT_TIMEOUT.get();
        if (timeout == null || timeout < 0)
            return 0;
        return timeout;
    }

    /**
     * 环错误构造守卫：NopException 构造期会解析本地化描述（重入 i18n 缓存装载）。构造期间置位，
     * 使重入的装载占位命中走降级路径（见 doGetObject），阻断递归构造。
     */
    static final ThreadLocal<Boolean> IN_RING_ERROR_CONSTRUCTION = new ThreadLocal<>();

    /**
     * 环/等待类型化错误构造（携带 resourcePath 与等待链快照）：构造期间置
     * {@link #IN_RING_ERROR_CONSTRUCTION}，i18n 解析重入装载时降级而非递归构造。
     */
    public static NopException ringError(ErrorCode code, String waitChain) {
        IN_RING_ERROR_CONSTRUCTION.set(Boolean.TRUE);
        try {
            NopException e = new NopException(code);
            if (waitChain != null)
                e.param(ARG_DEP_STACK, waitChain);
            return e;
        } finally {
            IN_RING_ERROR_CONSTRUCTION.set(Boolean.FALSE);
        }
    }

    /**
     * 跨线程装载/编译等待共用的等待图（plan 2504 裁定②机制单点）：边 = 等待线程 → 被等待线程。
     * 环检测 = 从目标线程出发回溯其等待链，链回到等待者自身即命中环（后到等待者抛类型化环错误，
     * 打断环上一条边，对方装载随后以失败复位收敛）。登记成本为每等待一次 O(链长) 内存操作。
     */
    public static final class LoadWaitGraph {
        private static final int MAX_CHAIN_DEPTH = 1000;
        private static final Map<Thread, Thread> WAIT_EDGES = new ConcurrentHashMap<>();

        private LoadWaitGraph() {
        }

        public static void register(Thread waiter, Thread target) {
            WAIT_EDGES.put(waiter, target);
        }

        public static void unregister(Thread waiter) {
            WAIT_EDGES.remove(waiter);
        }

        /**
         * 从 target 回溯等待链，链回到 waiter 即环命中。
         */
        public static boolean wouldCycle(Thread waiter, Thread target) {
            Thread t = target;
            for (int depth = 0; t != null && depth < MAX_CHAIN_DEPTH; depth++) {
                if (t == waiter)
                    return true;
                t = WAIT_EDGES.get(t);
            }
            return false;
        }

        /**
         * 等待链快照（诊断可见）：waiter → ... 形式。
         */
        public static String describeChain(Thread waiter) {
            StringJoiner sj = new StringJoiner(" -> ");
            Thread t = waiter;
            for (int depth = 0; t != null && depth < MAX_CHAIN_DEPTH; depth++) {
                sj.add(String.valueOf(t.getName()));
                t = WAIT_EDGES.get(t);
                if (t == waiter) {
                    sj.add("(cycle)");
                    break;
                }
            }
            return sj.toString();
        }
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

    private void reloadObject(IResourceObjectLoader<T> loader) {
        Object oldObj = normalizeObject(this.object);

        // C2-001 同链修复：装载经装载状态机编排（占位安装 + 监视器外装载 + 等待协议/环检测），
        // 与 getObject 共用同一不变式「监视器内只做同 key 去重+占位，装载在监视器外」
        Object obj = doGetObject(this.object, loader);

        if (obj != oldObj) {
            destroyObject(oldObj);
        }
    }


    /**
     * 旧对象延迟销毁（plan 2504 裁定③，C2-002 两销毁点之一）：reloadObject 发布新对象后旧对象退役，
     * 销毁动作（listener.onDestroyed + DestroyHelper.safeDestroy）经退役队列排空期执行，
     * 与 clear() 同一队列/FIFO 保序——onCreated（发布时已回调）→ onDestroyed 保序。
     */
    private void destroyObject(Object oldObj) {
        if (oldObj != null && oldObj != Null.NULL) {
            retireDestroyedObject(oldObj);
        }
    }

    /**
     * 销毁动作唯一执行位 = ResourceRetirementQueue 排空（关闭档 grace ≤0 退化为同步立即销毁 = 现行语义）。
     */
    private void retireDestroyedObject(Object oldObj) {
        if (oldObj == null || oldObj == Null.NULL)
            return;
        ResourceRetirementQueue.global().retire(oldObj, obj -> {
            if (listener != null) {
                listener.onDestroyed((T) obj);
            }
            DestroyHelper.safeDestroy(obj);
        });
    }
}