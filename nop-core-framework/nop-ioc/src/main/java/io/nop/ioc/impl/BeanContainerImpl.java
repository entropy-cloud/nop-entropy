/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.impl;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainerStartMode;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ICancellable;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.lang.IClassLoader;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.ClassHelper;
import io.nop.core.execution.TaskExecutionGraph;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.cache.ResourceCacheEntry.LoadWaitGraph;
import io.nop.ioc.IocConfigs;
import io.nop.ioc.api.BeanScopeContext;
import io.nop.ioc.api.IBeanContainerImplementor;
import io.nop.ioc.api.IBeanDefinition;
import io.nop.ioc.api.IBeanScope;
import io.nop.ioc.loader.AliasName;
import io.nop.ioc.loader.BeanDefinitionBuilder;
import io.nop.xlang.api.XLang;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static io.nop.ioc.IocConstants.PRODUCER_BEAN_PREFIX;
import static io.nop.ioc.IocErrors.ARG_BEAN;
import static io.nop.ioc.IocErrors.ARG_BEANS;
import static io.nop.ioc.IocErrors.ARG_BEAN_DEPENDS_CYCLE;
import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_BEAN_TYPE;
import static io.nop.ioc.IocErrors.ARG_CONTAINER_ID;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_CREATION_WAIT_TIMEOUT;
import static io.nop.ioc.IocErrors.ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE;
import static io.nop.ioc.IocErrors.ERR_IOC_CONTAINER_ALREADY_STARTED;
import static io.nop.ioc.IocErrors.ERR_IOC_CONTAINER_NOT_STARTED;
import static io.nop.ioc.IocErrors.ERR_IOC_MULTIPLE_BEAN_WITH_TYPE;
import static io.nop.ioc.IocErrors.ERR_IOC_NOT_PRODUCER_BEAN;
import static io.nop.ioc.IocErrors.ERR_IOC_PRODUCER_BEAN_NOT_INITED;
import static io.nop.ioc.IocErrors.ERR_IOC_START_JOIN_TIMEOUT;

public class BeanContainerImpl implements IBeanContainerImplementor {
    static final Logger LOG = LoggerFactory.getLogger(BeanContainerImpl.class);

    private final String id;
    private final Map<String, BeanDefinition> enabledBeans;
    private final Collection<BeanDefinition> optionalBeans;

    private final IBeanContainer parentContainer;

    private final List<BeanDefinition> orderedBeans;
    private volatile boolean running;
    private volatile boolean started;

    private final Map<Class<?>, BeanTypeMapping> beansByType = new ConcurrentHashMap<>();
    private final Map<Class<? extends Annotation>, List<BeanDefinition>> beansByAnnotation = new ConcurrentHashMap<>();

    private IConfigProvider configProvider = AppConfig.getConfigProvider();

    private IClassLoader classLoader = ClassHelper.getSafeClassLoader();
    private volatile IBeanClassIntrospection classIntrospection;

    // C1-001（plan 2549 裁定 A）：volatile——restart 窗口内并发 getBean 不得读到半初始化引用
    private volatile IBeanScope singletonScope;

    /**
     * C4-009（plan 2549 裁定 A）：启动代际令牌。start/restart 递增；handleStartError 携带启动时快照，
     * 代际不匹配 = 旧图失败收敛撞上新生代 → 放弃自毁 stop（否则旧图 stop 会摧毁新生代）。
     */
    private volatile long startGeneration;

    /** C4-009：startFuture 配对的启动代际（joinStart 侧快照来源）。 */
    private volatile long startFutureGeneration;

    private BeanContainerStartMode startMode = BeanContainerStartMode.DEFAULT;
    private Map<String, AliasName> aliases;

    private Cancellable cancellable;

    private boolean concurrentStart;

    private CompletableFuture<?> startFuture;

    /**
     * 启动失败终态标记（plan 2507 裁定①）：handleStartError 首次调用置位，失败终态的单一事实源。
     * join 侧据此判幂等（已成立则跳过收敛只抛异常），成功回调据此跳过 started 翻转。
     */
    private volatile boolean startFailed;

    /**
     * BD 域 per-BD 锁有界获取的单轮等待片（毫秒）：按片轮换使等待登记边能随 owner 换手刷新，
     * 防止陈旧边闭合假环。总预算由 nop.ioc.bean-init.wait-timeout 控制。
     */
    private static final long CREATION_LOCK_WAIT_SLICE_MILLIS = 100;

    public BeanContainerImpl(String id, Map<String, BeanDefinition> enabledBeans,
                             Collection<BeanDefinition> optionalBeans,
                             Map<String, AliasName> aliases, IBeanContainer parentContainer) {
        this.id = id;
        this.enabledBeans = enabledBeans;
        this.aliases = aliases;
        this.orderedBeans = BeanTopologySorter.INSTANCE.sort(enabledBeans);
        this.parentContainer = parentContainer;
        this.optionalBeans = optionalBeans == null ? Collections.emptyList() : optionalBeans;
    }

    BeanContainerImpl(String id, Map<String, BeanDefinition> enabledBeans,
                      Collection<BeanDefinition> optionalBeans,
                      Map<String, AliasName> aliases, IBeanContainer parentContainer, List<BeanDefinition> orderedBeans) {
        this.id = id;
        this.enabledBeans = enabledBeans;
        this.aliases = aliases;
        this.orderedBeans = orderedBeans;
        this.parentContainer = parentContainer;
        this.optionalBeans = optionalBeans == null ? Collections.emptyList() : optionalBeans;
    }

    @Override
    public IBeanContainer buildNewInstance(IBeanContainer parentContainer) {
        return new BeanContainerImpl(id, enabledBeans, optionalBeans, aliases, parentContainer, orderedBeans);
    }

    public void setConfigProvider(IConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    public void setClassLoader(IClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public IClassLoader getClassLoader() {
        return classLoader;
    }

    public IBeanClassIntrospection getClassIntrospection() {
        // 派生容器（buildNewInstance）未主动设置时才走懒初始化；volatile + 本地变量
        // 避免并发首次调用时重复发布不完整对象
        IBeanClassIntrospection introspection = classIntrospection;
        if (introspection == null) {
            introspection = new DefaultBeanClassIntrospection(classLoader);
            classIntrospection = introspection;
        }
        return introspection;
    }

    public void setClassIntrospection(IBeanClassIntrospection classIntrospection) {
        this.classIntrospection = classIntrospection;
    }

    public void setConcurrentStart(boolean concurrentStart) {
        this.concurrentStart = concurrentStart;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean containsBean(String name) {
        if (name.startsWith(PRODUCER_BEAN_PREFIX))
            name = name.substring(PRODUCER_BEAN_PREFIX.length());

        boolean b = enabledBeans.containsKey(name);
        if (!b) {
            if (parentContainer != null) {
                name = normalizeAlias(name);
                b = parentContainer.containsBean(name);
            }
        }
        return b;
    }

    private String normalizeAlias(String name) {
        AliasName alias = aliases.get(name);
        return alias == null ? name : alias.getName();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        return getBean(name, false, null);
    }

    @Nonnull
    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        return getBeanByType(clazz, false, null);
    }

    @Override
    public <T> T tryGetBeanByType(Class<T> requiredType) {
        checkStarted();
        BeanDefinition bean = findBeanByType(requiredType);
        if (bean == null) {
            if (parentContainer != null) {
                return parentContainer.tryGetBeanByType(requiredType);
            } else {
                return null;
            }
        }
        return (T) getBean0(bean, false, false, new BeanCreationContext(bean));
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        checkStarted();

        BeanTypeMapping mapping = BeanFinder.getBeansByType(beansByType, orderedBeans, clazz);
        Map<String, T> ret = new HashMap<>();
        if (parentContainer != null) {
            ret.putAll(parentContainer.getBeansOfType(clazz));
        }
        for (BeanDefinition beanDef : mapping.getBeans()) {
            T bean = (T) getBean0(beanDef, false, false, new BeanCreationContext(beanDef));
            ret.put(beanDef.getId(), bean);
        }
        return ret;
    }

    public IBeanDefinition getBeanDefinition(String name) {
        IBeanDefinition bean = enabledBeans.get(name);
        if (bean == null) {
            if (parentContainer instanceof IBeanContainerImplementor) {
                name = normalizeAlias(name);
                bean = ((IBeanContainerImplementor) parentContainer).getBeanDefinition(name);
            }
        }
        return bean;
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        checkStarted();
        List<BeanDefinition> annBeans = BeanFinder.getBeansByAnnotation(beansByAnnotation, orderedBeans, annClass);
        Map<String, Object> ret = new HashMap<>();
        if (parentContainer != null) {
            ret.putAll(parentContainer.getBeansWithAnnotation(annClass));
        }
        for (BeanDefinition bean : annBeans) {
            Object instance = getBean0(bean, false, false, new BeanCreationContext(bean));
            ret.put(bean.getId(), instance);
        }
        return ret;
    }

    @Override
    public String getBeanScope(String name) {
        BeanDefinition beanDef = enabledBeans.get(name);
        if (beanDef == null) {
            if (parentContainer != null) {
                return parentContainer.getBeanScope(normalizeAlias(name));
            }

            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
        }
        return beanDef.getScope();
    }

    @Override
    public Class<?> getBeanClass(String name) {
        BeanDefinition beanDef = enabledBeans.get(name);
        if (beanDef == null) {
            if (parentContainer != null) {
                return parentContainer.getBeanClass(normalizeAlias(name));
            }

            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
        }
        return beanDef.getBeanClass();
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        BeanTypeMapping mapping = BeanFinder.getBeansByType(beansByType, orderedBeans, clazz);
        boolean b = !mapping.isEmpty();
        if (!b) {
            if (parentContainer != null)
                b = parentContainer.containsBeanType(clazz);
        }
        return b;
    }

    @Override
    public Object getBean(@Nonnull String name, boolean includeCreating, BeanCreationContext beanCtx) {
        checkStarted();

        String rawName = name;
        boolean onlyProducer = name.startsWith(PRODUCER_BEAN_PREFIX);
        if (onlyProducer) {
            name = name.substring(PRODUCER_BEAN_PREFIX.length());
        }

        BeanDefinition bean = enabledBeans.get(name);
        if (bean == null) {
            if (parentContainer != null)
                return parentContainer.getBean(normalizeAlias(rawName));

            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_NAME).param(ARG_BEAN_NAME, name);
        }

        if (onlyProducer && bean.getBeanMethod() == null)
            throw new NopException(ERR_IOC_NOT_PRODUCER_BEAN).param(ARG_BEAN_NAME, name);

        if (beanCtx == null) {
            beanCtx = new BeanCreationContext(bean);
            Object ret = getBean0(bean, onlyProducer, includeCreating, beanCtx);
            beanCtx.flushActions();
            return ret;
        } else {
            return getBean0(bean, onlyProducer, includeCreating, beanCtx);
        }
    }

    @Override
    public <T> T getBeanByType(Class<T> requiredType, boolean includeCreating, BeanCreationContext beanCtx) {
        checkStarted();
        BeanDefinition bean = findBeanByType(requiredType);
        if (bean == null) {
            if (parentContainer != null)
                return parentContainer.getBeanByType(requiredType);

            throw new NopException(ApiErrors.ERR_IOC_UNKNOWN_BEAN_FOR_TYPE).param(ARG_BEAN_TYPE, requiredType)
                    .param(ARG_CONTAINER_ID, getId());
        }

        if (beanCtx == null)
            beanCtx = new BeanCreationContext(bean);
        return (T) getBean0(bean, false, includeCreating, beanCtx);
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        BeanDefinition beanDef = findBeanByType(beanType);
        if (beanDef == null) {
            if (parentContainer != null)
                return parentContainer.findAutowireCandidate(beanType);
            return null;
        }
        return beanDef.getId();
    }

    private BeanDefinition findBeanByType(Class<?> beanType) {
        BeanTypeMapping mapping = BeanFinder.getBeansByType(beansByType, orderedBeans, beanType);
        if (mapping.isEmpty())
            return null;
        if (mapping.getOtherPrimaryBean() != null)
            throw new NopException(ERR_IOC_MULTIPLE_BEAN_WITH_TYPE).param(ARG_BEAN_TYPE, beanType)
                    .param(ARG_BEANS, mapping.getBeans());

        BeanDefinition bean = mapping.getPrimaryBean();
        if (bean == null) {
            throw new NopException(ERR_IOC_MULTIPLE_BEAN_WITH_TYPE).param(ARG_BEAN_TYPE, beanType)
                    .param(ARG_BEANS, mapping.getBeans());
        }
        return bean;
    }

    public void refreshConfig(String beanId) {
        IBeanDefinition beanDef = getBeanDefinition(beanId);
        if (beanDef instanceof BeanDefinition) {
            BeanDefinition def = (BeanDefinition) beanDef;
            if (def.getRefreshConfigMethod() != null || def.getBeanModel().getIocRefreshConfig() != null) {
                IBeanScope beanScope = getBeanScope(def);
                Object bean = beanScope.get(def.getId());
                if (bean != null) {
                    def.onRefreshConfig(bean, this, beanScope);
                }
            }
        }
    }

    private Object getBean0(BeanDefinition beanDef, boolean onlyProducer, boolean includeCreating, BeanCreationContext beanCtx) {
        IBeanScope beanScope = getBeanScope(beanDef);

        if (includeCreating && beanScope != null) {
            ProducedBeanInstance bean = beanScope.get(beanDef.getId());
            if (bean != null) {
                Object createdBean = beanDef.getBeanInstance(bean, onlyProducer, includeCreating, beanCtx);
                // 生产者bean尚未执行完init/beanMethod，说明依赖关系未满足，不允许返回
                if (createdBean == null)
                    throw new NopException(ERR_IOC_PRODUCER_BEAN_NOT_INITED).param(ARG_BEAN, beanDef)
                            .param(ARG_BEAN_NAME, beanDef.getId());

                return createdBean;
            }
        }

        ProducedBeanInstance beanInstance;
        if (beanScope == null) {
            // prototype 同样使用创建中标记做环检测：构造器自引用/互引环在 prototype 语义下
            // 每次注入都新建实例，放任递归会 StackOverflowError 而非清晰的环错误
            if (beanDef.isInCreationByCurrentThread()) {
                throw new NopException(ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE)
                        .param(ARG_BEAN_NAME, beanDef.getId()).param(ARG_BEAN_DEPENDS_CYCLE, beanDef);
            }
            beanDef.markInCreation();
            try {
                beanInstance = beanDef.createInstance(null, this, beanCtx);
            } finally {
                beanDef.clearInCreation();
            }
            beanDef.setupInstance(beanInstance, null, this, beanCtx);
        } else {
            beanInstance = getOrCreateSingleton(beanDef, beanScope, beanCtx);
        }

        return beanDef.getBeanInstance(beanInstance, onlyProducer, includeCreating, beanCtx);
    }

    /**
     * 单例创建临界区（plan 2507 裁定②）：全仓唯一 synchronized(beanDef) 站点迁移为 per-BD
     * {@link ReentrantLock} 有界获取（锁对象随 BeanDefinition 生命周期，可重入语义与 monitor 一致）。
     * 等待获取前经 {@link LoadWaitGraph}（机制单点复用 plan 2504 LOCK-001 跨域统一等待图）登记
     * 「当前线程 → 目标创建 owner」边并回溯环，命中即抛类型化环错误；有界获取按片轮换，
     * 每轮重读 owner 刷新登记边防假环误报，预算耗尽抛类型化等待超时错误。owner 为 null
     * （目标尚无创建者）时退化为无环检语义，由有界兜底承载。
     *
     * <p>不可见边（构造器级 type-injection / factoryBean / supplier / 表达式等）不进构建期拓扑序
     * 与 order 校验，仅入运行期等待登记——跨线程各持一 BD 锁互等的锁序环在此即时类型化收敛，
     * 而非 synchronized 无超时永久挂死。</p>
     */
    private ProducedBeanInstance getOrCreateSingleton(BeanDefinition beanDef, IBeanScope beanScope,
                                                      BeanCreationContext beanCtx) {
        Thread current = Thread.currentThread();
        ReentrantLock creationLock = beanDef.getCreationLock();
        Integer timeoutConfig = IocConfigs.CFG_IOC_BEAN_INIT_WAIT_TIMEOUT.get();
        long timeoutMillis = timeoutConfig == null ? 0 : timeoutConfig;
        long deadline = timeoutMillis > 0 ? CoreMetrics.currentTimeMillis() + timeoutMillis : 0L;

        try {
            while (true) {
                if (BeanWaitErrors.isConstructingRingError()) {
                    // 错误构造守卫：本地化解析重入等待路径时快速失败，阻断递归等待（沿 ResourceCacheEntry 先例）
                    throw ringError(ERR_IOC_BEAN_CREATION_WAIT_TIMEOUT, beanDef.getId());
                }

                // 每轮重读 owner 刷新登记边：owner 换手后陈旧边会闭合假环导致误报。
                // owner 为 null（目标尚无创建者）时不登记，退化为无环检语义。
                Thread owner = beanDef.getInCreationThread();
                if (owner != null && owner != current) {
                    LoadWaitGraph.register(current, owner);
                    if (LoadWaitGraph.wouldCycle(current, owner)) {
                        throw ringError(ERR_IOC_BEAN_CREATION_CIRCULAR_WAIT, beanDef.getId());
                    }
                }

                boolean acquired;
                try {
                    if (deadline > 0) {
                        long remaining = deadline - CoreMetrics.currentTimeMillis();
                        if (remaining <= 0) {
                            // 有界兜底：等待图回溯存在竞态漏检窗，超时类型化（携带等待链快照，诊断可见）
                            throw ringError(ERR_IOC_BEAN_CREATION_WAIT_TIMEOUT, beanDef.getId());
                        }
                        acquired = creationLock.tryLock(Math.min(remaining, CREATION_LOCK_WAIT_SLICE_MILLIS),
                                TimeUnit.MILLISECONDS);
                    } else {
                        // ≤0 不设上界档（现行无界语义）：仍按片轮换以持续刷新等待登记边
                        acquired = creationLock.tryLock(CREATION_LOCK_WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }
                if (!acquired)
                    continue;

                ProducedBeanInstance instance;
                try {
                    // 成功获取后注销等待登记：本线程等待状态已解除
                    LoadWaitGraph.unregister(current);

                    ProducedBeanInstance existing = beanScope.get(beanDef.getId());
                    if (existing != null)
                        return existing;

                    // 构造器循环依赖检测：当前线程正在创建该 bean（构造器参数解析中，尚未 scope.add），
                    // 同线程重入只可能来自构造器依赖环。此时无法通过早期暴露返回半成品，
                    // 继续递归会静默产生重复单例（B 持有 A2、容器注册 A1），必须显式失败。
                    if (beanDef.isInCreationByCurrentThread()) {
                        throw new NopException(ERR_IOC_BEAN_DEPENDS_GRAPH_CONTAINS_CYCLE)
                                .param(ARG_BEAN_NAME, beanDef.getId()).param(ARG_BEAN_DEPENDS_CYCLE, beanDef);
                    }
                    LOG.info("nop.new-bean:{}", beanDef);
                    beanDef.markInCreation();
                    try {
                        instance = beanDef.createInstance(beanScope, this, beanCtx);
                    } finally {
                        beanDef.clearInCreation();
                    }
                } finally {
                    creationLock.unlock();
                }

                // 属性赋值与init动作登记在锁外执行，避免在容器锁内回调用户代码
                beanDef.setupInstance(instance, beanScope, this, beanCtx);
                return instance;
            }
        } finally {
            LoadWaitGraph.unregister(current);
        }
    }

    /**
     * 环/等待类型化错误构造：携带等待链快照与目标 beanId，构造期间置
     * {@link BeanWaitErrors#IN_RING_ERROR_CONSTRUCTION} 守卫（沿 ResourceCacheEntry.ringError 先例）。
     */
    private static NopException ringError(ErrorCode code, String beanId) {
        NopException e = BeanWaitErrors.ringError(code, LoadWaitGraph.describeChain(Thread.currentThread()));
        e.param(ARG_BEAN_NAME, beanId);
        return e;
    }

    IBeanScope getBeanScope(BeanDefinition bean) {
        IBeanScope beanScope;
        if (bean.isSingleton()) {
            beanScope = singletonScope;
        } else if (bean.isPrototype()) {
            beanScope = null;
        } else {
            beanScope = BeanScopeContext.instance().getScope(this, bean.getScope());
        }
        return beanScope;
    }

    public boolean isStarted() {
        return started;
    }

    public synchronized void restart() {
        stop();
        start();
    }

    public BeanContainerStartMode getStartMode() {
        return startMode;
    }

    public void setStartMode(BeanContainerStartMode startMode) {
        this.startMode = startMode;
    }

    @Override
    public Object getConfigValue(String varName) {
        return configProvider.getConfigValue(varName, null);
    }

    @Override
    public IConfigProvider getConfigProvider() {
        return configProvider;
    }

    @Override
    public synchronized void start() {
        if (running)
            throw new NopException(ERR_IOC_CONTAINER_ALREADY_STARTED).param(ARG_CONTAINER_ID, id);

        startFuture = null;
        // 失败终态属于单次 start 尝试：新启动轮次重置（restart 场景不延续上一轮失败终态）
        startFailed = false;
        LOG.info("nop.ioc.start-container:containerId={}", getId());

        // C4-009：新启动轮次开启新代际（restart 旧图失败收敛以此识别「新生代已接管」）
        startGeneration++;
        startFutureGeneration = startGeneration;
        running = true;
        singletonScope = new BeanScopeImpl(ApiConstants.BEAN_SCOPE_SINGLETON, XLang.newEvalScope(), this);

        try {
            List<BeanDefinition> startBeans = new ArrayList<>();

            for (BeanDefinition bean : orderedBeans) {
                if (bean.isSingleton()) {
                    if (startMode == BeanContainerStartMode.ALL_LAZY) {
                        // 只创建具有delayMethod的bean
                        if (bean.hasDelayMethod() && !bean.isLazyInit() || bean.isIocForceInit()) {
                            startBean(startBeans, bean);
                        }
                    } else if (startMode == BeanContainerStartMode.ALL_EAGER || !bean.isLazyInit()) {
                        startBean(startBeans, bean);
                    }
                }
            }

            if (concurrentStart) {
                // C4-009：快照本代际——whenComplete 回调线程据此与当前代际比对
                final long gen = startGeneration;
                startFuture = asyncStartBeans(startBeans).whenComplete((ret, err) -> {
                    if (err != null) {
                        handleStartError(err, gen);
                    } else if (!startFailed) {
                        try {
                            runLazyProperties();
                            runDelayMethod();
                            started = true;
                        } catch (RuntimeException | Error e) {
                            // C4-008 F5：成功回调异常不再静默，统一落失败终态并向 startFuture 传播
                            handleStartError(e, gen);
                            throw e;
                        }
                    }
                    LOG.info("nop.ioc.async-start-finished:{}", getId());
                });
            } else {
                runLazyProperties();
                runDelayMethod();
                started = true;
                LOG.info("nop.ioc.start-finished:{}", getId());
            }
        } catch (Exception e) {
            // dumpCreations();
            handleStartError(e, startGeneration);
            throw e;
        }
    }

    /**
     * 启动失败终态汇聚点（plan 2507 裁定①）：首次调用落定失败终态——置 startFailed 标记 + stop()
     * （cancel 在飞/孤儿启动图、销毁已建 bean、释放非 daemon 线程）。幂等：失败终态已成立时
     * （whenComplete 先行或 join 侧已收敛）跳过重复收敛，防止二次执行 bean destroy 用户代码
     * （stop 不置空 singletonScope，二次调用会重复 destroy）。
     */
    void handleStartError(Throwable e) {
        handleStartError(e, startGeneration);
    }

    /**
     * 启动失败终态汇聚点。C4-009（plan 2549 裁定 A）：携带启动时快照的代际——
     * 当前代际已推进（restart/new start 已接管）时放弃自毁 stop，仅记录旧图失败
     * （否则旧图 whenComplete → stop() 会摧毁新生代的 singletonScope 与已建 bean）。
     */
    void handleStartError(Throwable e, long generation) {
        if (startFailed)
            return;
        // C4-009（执行期勘误 E-2549-3）：代际校验必须先于 startFailed 置位——旧代失败若先污染
        // startFailed，新代 whenComplete 的 !startFailed 分支会被跳过（started 永不翻转）
        if (generation != startGeneration) {
            LOG.warn("nop.warn.ioc.stale-start-error:containerId={},gen={},currentGen={}",
                    getId(), generation, startGeneration, e);
            return;
        }
        startFailed = true;
        LOG.error("nop.err.ioc.start-failed", e);
        try {
            stop();
        } catch (Exception e2) {
            LOG.error("nop.err.ioc.stop-failed", e2);
        }
    }

    @Override
    public void awaitStartFinished() {
        Integer timeoutConfig = IocConfigs.CFG_IOC_APP_BEANS_CONTAINER_START_JOIN_TIMEOUT.get();
        long timeoutMillis = timeoutConfig == null ? 0 : timeoutConfig;
        joinStart(timeoutMillis);
    }

    /**
     * 并发启动 join（plan 2507 裁定①，awaitStartFinished 的有界等价，join 落位于
     * IocCoreInitializer.initialize() 启动序）：等待启动图终态。成功正常返回；失败终态
     * （startFuture 异常终态，含 C4-008 F5 成功回调异常）快速失败重抛原始异常；等待超时先做
     * 失败终态收敛（handleStartError：cancel 孤儿图 + stop + 失败标记，幂等）再抛类型化
     * join 超时错误（C4-008 F2 Error 孤儿 / F4 永久 pending 由本档兜底闭合）。
     * timeoutMillis ≤0 = 不设上界档（现行无界语义）。
     */
    private void joinStart(long timeoutMillis) {
        CompletableFuture<?> future = startFuture;
        if (future == null)
            return;

        long deadline = timeoutMillis > 0 ? CoreMetrics.currentTimeMillis() + timeoutMillis : 0L;
        while (true) {
            if (future.isDone()) {
                // 失败终态保留异常终态可查询：快速失败重抛原始异常（语义同 FutureHelper.syncGet 重抛）
                try {
                    future.join();
                    return;
                } catch (CompletionException | CancellationException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    // 失败终态应已由 whenComplete → handleStartError 落定；此处幂等兜底（携带 startFuture 配对代际）
                    handleStartError(cause);
                    throw NopException.adapt(cause);
                }
            }
            long remaining = deadline > 0 ? deadline - CoreMetrics.currentTimeMillis() : 0L;
            if (deadline > 0 && remaining <= 0) {
                NopException timeoutErr = new NopException(ERR_IOC_START_JOIN_TIMEOUT).param(ARG_CONTAINER_ID, id);
                // 失败终态收敛（幂等）：cancel 孤儿图 + stop + 失败标记，再抛类型化超时错误
                handleStartError(timeoutErr);
                // F2 孤儿图（Error 逃逸 runTask catch(Exception)）下图 future 永不完成：
                // 以超时错误完成 startFuture，使失败终态可持续查询（再次 awaitStartFinished 快速失败）
                future.completeExceptionally(timeoutErr);
                throw timeoutErr;
            }
            try {
                if (deadline > 0) {
                    future.get(remaining, TimeUnit.MILLISECONDS);
                } else {
                    future.get();
                }
            } catch (TimeoutException e) {
                // deadline 总控判定，循环重查
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                // C4-009：joinStart 侧快照取 startFuture 配对代际（不读当前代际——restart 可能已推进）
                handleStartError(cause, startFutureGeneration);
                throw NopException.adapt(cause);
            } catch (CancellationException e) {
                handleStartError(e, startFutureGeneration);
                throw e;
            }
        }
    }

    CompletableFuture<Void> asyncStartBeans(List<BeanDefinition> startBeans) {
        TaskExecutionGraph graph = new TaskExecutionGraph(GlobalExecutors.globalWorker(), "ioc-container-start");
        for (BeanDefinition bean : startBeans) {
            graph.addTaskWithDepends(bean.getId(),
                    cancelToken -> {
                        // beanCreations.add(new BeanCreation(Thread.currentThread().getName(), bean.getId(), graph.getDepends(bean.getId()), false));
                        BeanCreationContext beanCtx = new BeanCreationContext(bean,false);
                        getBean0(bean, true, false, beanCtx);
                        beanCtx.flushActions();
                        // beanCreations.add(new BeanCreation(Thread.currentThread().getName(), bean.getId(), graph.getDepends(bean.getId()), true));
                        return null;
                    },
                    bean.getResolvedDepends());
        }
        graph.analyze();
        this.cancellable = new Cancellable();
        return graph.executeAsync(this.cancellable);
    }

    void startBean(List<BeanDefinition> startBeans, BeanDefinition bean) {
        if (concurrentStart) {
            startBeans.add(bean);
        } else {
            BeanCreationContext beanCtx = new BeanCreationContext(bean,false);
            getBean0(bean, true, false, beanCtx);
            beanCtx.flushActions();
        }
    }

    void runDelayMethod() {
        for (BeanDefinition beanDef : orderedBeans) {
            if (beanDef.isSingleton() && beanDef.hasDelayMethod()) {
                IBeanScope beanScope = getBeanScope(beanDef);
                ProducedBeanInstance instance = beanScope.get(beanDef.getId());
                if (instance != null)
                    instance.checkBeanDelayActionRun();
            }
        }
        LOG.info("nop.ioc.run-delay-method-finished");
    }

    void runLazyProperties() {
        for (BeanDefinition beanDef : orderedBeans) {
            if (beanDef.isSingleton() && beanDef.hasLazyProperties()) {
                IBeanScope beanScope = getBeanScope(beanDef);
                ProducedBeanInstance instance = beanScope.get(beanDef.getId());
                if (instance != null)
                    instance.checkBeanLazyPropSet();
            }
        }
        LOG.info("nop.ioc.run-lazy-properties-finished");
    }

    @Override
    public synchronized void stop() {
        LOG.info("nop.ioc.stop-container:containerId={}", getId());
        // C6-008（plan 2549 裁定 A）：先执行销毁回调（scope close + 容器停止回调），再清 running/started——
        // 原顺序先灭状态，销毁回调内的 checkStarted 抛错使销毁链中断
        // C6-007（plan 2549；closure audit MJ-2 勘正）：收集口径 Throwable 且**状态清理先于重抛**——
        // destroy 抛 Error 不得跳过 running/started 清理与 cancellable 取消
        Throwable ex = null;
        if (singletonScope != null) {
            try {
                singletonScope.close();
            } catch (Throwable e) {
                LOG.error("nop.err.ioc.singleton-scope-close-fail:containerId={}", getId(), e);
                ex = e;
            }
        }
        try {
            BeanScopeContext.instance().onContainerStop(this);
        } catch (Throwable e) {
            LOG.error("nop.err.ioc.container-stop-callback-fail:containerId={}", getId(), e);
            if (ex == null)
                ex = e;
        }

        running = false;
        started = false;
        if (cancellable != null) {
            cancellable.cancel(ICancellable.CANCEL_REASON_STOP);
            cancellable = null;
        }

        if (ex instanceof RuntimeException)
            throw (RuntimeException) ex;
        if (ex instanceof Error)
            throw (Error) ex;
        if (ex != null)
            throw NopException.adapt(ex);
    }

    void checkStarted() {
        if (!started && !running)
            throw new NopException(ERR_IOC_CONTAINER_NOT_STARTED).param(ARG_CONTAINER_ID, getId());
    }

    @Override
    public void destroyBean(String beanName, Object bean) {
        BeanDefinition beanDef = enabledBeans.get(beanName);
        if (beanDef != null) {
            IBeanScope beanScope = getBeanScope(beanDef);
            beanDef.destroyBean(bean, beanScope, this);
        }
    }

    @Override
    public boolean supportInjectTo() {
        return true;
    }

    @Override
    public void injectTo(Object bean) {
        LOG.debug("nop.inject-props-to-bean:{}", bean);
        checkStarted();
        Class<?> beanClass = bean.getClass();
        BeanDefinition beanDef = new BeanDefinitionBuilder(classLoader, getClassIntrospection(), this)
                .useBeans(this.enabledBeans).buildForAutowire(beanClass);

        BeanCreationContext beanCtx = new BeanCreationContext(beanDef);
        beanDef.initProps(bean, this, null, beanCtx);
        beanCtx.flushActions();
    }

    public XNode toConfigNode() {
        List<BeanDefinition> beans = new ArrayList<>();
        beans.addAll(orderedBeans);
        beans.addAll(optionalBeans);
        return new BeanContainerDumper().toConfigNode(beans);
    }

    @Override
    public Map<String, IBeanDefinition> getBeanDefinitionsByType(Class<?> clazz) {
        checkStarted();

        BeanTypeMapping mapping = BeanFinder.getBeansByType(beansByType, orderedBeans, clazz);
        Map<String, IBeanDefinition> ret = new HashMap<>();
        if (parentContainer instanceof IBeanContainerImplementor) {
            ret.putAll(((IBeanContainerImplementor) parentContainer).getBeanDefinitionsByType(clazz));
        }
        for (BeanDefinition beanDef : mapping.getBeans()) {
            ret.put(beanDef.getId(), beanDef);
        }
        return ret;
    }

    @Override
    public Map<String, IBeanDefinition> getBeanDefinitionsByAnnotation(Class<? extends Annotation> annClass) {
        checkStarted();
        List<BeanDefinition> annBeans = BeanFinder.getBeansByAnnotation(beansByAnnotation, orderedBeans, annClass);
        Map<String, IBeanDefinition> ret = new HashMap<>();
        if (parentContainer instanceof IBeanContainerImplementor) {
            ret.putAll(((IBeanContainerImplementor) parentContainer).getBeanDefinitionsByAnnotation(annClass));
        }
        for (BeanDefinition bean : annBeans) {
            ret.put(bean.getId(), bean);
        }
        return ret;
    }
}