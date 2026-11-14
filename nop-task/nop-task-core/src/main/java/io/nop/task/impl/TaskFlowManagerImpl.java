package io.nop.task.impl;

import io.nop.api.core.time.CoreMetrics;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.concurrent.executor.IScheduledExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.ratelimit.DefaultRateLimiter;
import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.concurrent.semaphore.DefaultSemaphore;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.commons.metrics.GlobalMeterRegistry;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.component.version.ResourceVersionHelper;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepLib;
import io.nop.task.TaskConstants;
import io.nop.task.TaskErrors;
import io.nop.task.builder.ITaskFlowBuilder;
import io.nop.task.builder.ITaskStepLibBuilder;
import io.nop.task.builder.TaskFlowBuilder;
import io.nop.task.builder.TaskStepLibBuilder;
import io.nop.task.metrics.TaskFlowMetricsImpl;
import io.nop.task.model.TaskFlowModel;
import io.nop.task.state.DefaultTaskStateStore;
import io.nop.xlang.xdsl.DslModelParser;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.task.TaskConfigs.CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS;
import static io.nop.task.TaskConfigs.CFG_TASK_GLOBAL_GATE_MAX_ENTRIES;
import static io.nop.task.TaskErrors.ARG_TASK_INSTANCE_ID;
import static io.nop.task.TaskErrors.ERR_TASK_NO_PERSIST_STATE_STORE;
import static io.nop.task.TaskErrors.ERR_TASK_UNKNOWN_TASK_INSTANCE;

public class TaskFlowManagerImpl implements ITaskFlowManagerImplementor {
    private static final Logger LOG = LoggerFactory.getLogger(TaskFlowManagerImpl.class);

    private IScheduledExecutor scheduledExecutor;

    private ITaskStateStore taskStateStore;

    private ITaskStateStore nonPersistStateStore = DefaultTaskStateStore.INSTANCE;

    // plan 364 [维度05-04]：全局限流器/信号量改用强引用注册表——此前为 Caffeine 有界缓存
    // （maximumSize=10000），驱逐在用实例会分裂 permit 池（持有者向旧实例 release、新获取者
    // 拿新实例各自计数，maxConcurrency/速率被拆分失效）。信号量语义要求精确计数，正确性优先
    // 于有界内存；key 基数增长以 WARN 提示（每注册表一次），原容量配置转为准入告警阈值。
    // plan 2514 MA-08（1.5-C5-003，裁定⑤）：在上述 permit 池完整性约束之上落键空间治理——
    // lastAccess + 空闲逐出（逐出执行位 = 同 key compute 内联，CHM bin 锁串行 + 引用一致复核，
    // 无独立清扫位/无后台线程）；entry 为 GlobalGate 持有 lastAccess。
    private final ConcurrentHashMap<String, GlobalGate<IRateLimiter>> globalRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GlobalGate<ISemaphore>> globalSemaphores = new ConcurrentHashMap<>();
    // 超限 WARN 改周期性（60s 间隔，plan 2514 MA-08；WARN-once 不可操作）
    private final AtomicLong rateLimiterCapWarnAt = new AtomicLong();
    private final AtomicLong semaphoreCapWarnAt = new AtomicLong();
    // W-03（CRA 8.48 R4-08，MA-10 裁定 A）：配置固化告警全局周期化（同 60s 间隔形态复用——
    // 修复前 rate-limiter 侧每调用打印无抑制；semaphore 侧补对称告警）。失效路径裁定为
    // owner doc 文档声明形态（「global 闸门限流参数变更需重启生效」——模型热更新不触注册表，
    // 2514 键空间治理产物零重开），frozen WARN 即其可操作信号
    private final AtomicLong rateLimiterFrozenWarnAt = new AtomicLong();
    private final AtomicLong semaphoreFrozenWarnAt = new AtomicLong();

    private static final long CAP_WARN_INTERVAL_MS = 60_000L;

    /**
     * plan 2514 MA-08：注册表 entry 持有体——gate 实例 + lastAccess（空闲逐出判据输入）。
     */
    static final class GlobalGate<T> {
        final T gate;
        volatile long lastAccess;

        GlobalGate(T gate) {
            this.gate = gate;
            this.lastAccess = CoreMetrics.nanoTime(); // plan 2572 P4：lastAccess 单调域（墙钟前跳会提前逐出在用限流器=配额重置绕过）
        }
    }

    public TaskFlowManagerImpl() {
        registerRegistrySizeGauges();
    }

    /**
     * plan 2514 MA-08 观测半面（MA-09 全量治理归 R4-08）：registry.size gauge，
     * 两注册表各一（tag registry=rate-limiter/semaphore）。
     */
    private void registerRegistrySizeGauges() {
        MeterRegistry meterRegistry = GlobalMeterRegistry.instance();
        Gauge.builder("nop.task.global-gate.registry-size", globalRateLimiters, Map::size)
                .tag("registry", "rate-limiter")
                .description("Global rate limiter registry size (plan 2514 MA-08)")
                .register(meterRegistry);
        Gauge.builder("nop.task.global-gate.registry-size", globalSemaphores, Map::size)
                .tag("registry", "semaphore")
                .description("Global semaphore registry size (plan 2514 MA-08)")
                .register(meterRegistry);
    }

    public void setNonPersistStateStore(ITaskStateStore stateStore) {
        this.nonPersistStateStore = stateStore;
    }

    public void setScheduledExecutor(IScheduledExecutor scheduledExecutor) {
        this.scheduledExecutor = scheduledExecutor;
    }

    @Override
    public IScheduledExecutor getScheduledExecutor() {
        if (scheduledExecutor == null)
            return GlobalExecutors.globalTimer();
        return scheduledExecutor;
    }

    // W-13（裁定 B）：执行级 fencing 在途注册表（进程生命周期=注册表生命周期）
    private final Set<String> activeExecutions = ConcurrentHashMap.newKeySet();

    @Override
    public boolean tryBeginExecution(String taskInstanceId) {
        return activeExecutions.add(taskInstanceId);
    }

    @Override
    public void endExecution(String taskInstanceId) {
        if (taskInstanceId != null)
            activeExecutions.remove(taskInstanceId);
    }

    @Override
    public IThreadPoolExecutor getThreadPoolExecutor(IBeanProvider beanProvider, String executorBean) {
        IThreadPoolExecutor executor = GlobalExecutors.getExecutor(executorBean);
        if (executor != null) {
            // W-05（CRA 8.49 R4-09，1.5-C4-004 裁定 C）：保留名遮蔽显式化——同名 IoC bean
            // 存在时被 GlobalExecutors 保留名静默遮蔽（线程名与意图一致、线程转储不可诊断）；
            // containsBean 判存在零实例化。per-call WARN（非逐调用高频路径，不引入 per-name 去重状态）
            if (beanProvider != null && beanProvider.containsBean(executorBean)) {
                LOG.warn("nop.task.executor-reserved-name-shadowing:executorBean={},containerBeanExists=true; "
                        + "IoC bean is shadowed by GlobalExecutors reserved name", executorBean);
            }
            return executor;
        }

        Object bean = beanProvider.getBean(executorBean);
        // W-05（裁定 C）：错类型 typed 拒绝——修复前裸 CCE 无 bean 名/实际类型上下文
        if (!(bean instanceof IThreadPoolExecutor))
            throw new NopException(TaskErrors.ERR_TASK_EXECUTOR_BEAN_INVALID)
                    .param(TaskErrors.ARG_EXECUTOR_BEAN, executorBean)
                    .param(TaskErrors.ARG_ACTUAL_TYPE, bean == null ? "null" : bean.getClass().getName());
        return (IThreadPoolExecutor) bean;
    }

    @Inject
    public void setTaskStateStore(@Nullable ITaskStateStore taskStateStore) {
        this.taskStateStore = taskStateStore;
    }

    @Override
    public ITaskRuntime newTaskRuntime(ITask task, boolean saveState, IServiceContext svcCtx, IEvalScope scope) {
        ITaskStateStore stateStore = saveState ? requirePersistStateState() : nonPersistStateStore;
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, false);

        ITaskState taskState = stateStore.newTaskState(task.getTaskName(), task.getTaskVersion(), taskRt);
        taskRt.setTaskState(taskState);

        prepareTaskRuntime(taskRt, task);
        return taskRt;
    }

    protected void prepareTaskRuntime(TaskRuntimeImpl taskRt, ITask task) {
        String taskName = task.getTaskName();
        long taskVersion = task.getTaskVersion();
        taskRt.setMetrics(new TaskFlowMetricsImpl(GlobalMeterRegistry.instance(), null, taskName, taskVersion));
    }

    private ITaskStateStore requirePersistStateState() {
        if (this.taskStateStore == null)
            throw new NopException(ERR_TASK_NO_PERSIST_STATE_STORE);
        return this.taskStateStore;
    }

    @Override
    public ITaskRuntime getTaskRuntime(String taskInstanceId, IServiceContext svcCtx, IEvalScope scope) {
        ITaskStateStore stateStore = requirePersistStateState();
        TaskRuntimeImpl taskRt = new TaskRuntimeImpl(this, stateStore, svcCtx, scope, true);
        ITaskState taskState = stateStore.loadTaskState(taskInstanceId, taskRt);
        if (taskState == null)
            throw new NopException(ERR_TASK_UNKNOWN_TASK_INSTANCE)
                    .param(ARG_TASK_INSTANCE_ID, taskInstanceId);
        taskRt.setTaskState(taskState);
        // resume 路径与 fresh 执行对称启用 task/step 指标（plan 349 Phase 5，修复 check2 P2）：
        // 修复前 metrics 保持 EmptyTaskFlowMetrics，恢复执行的任务不记录任何指标，监控口径失真
        taskRt.setMetrics(new TaskFlowMetricsImpl(GlobalMeterRegistry.instance(), null,
                taskState.getTaskName(), taskState.getTaskVersion() == null ? 0 : taskState.getTaskVersion()));
        return taskRt;
    }

    @Override
    public ITask getTask(String taskName, long taskVersion) {
        TaskFlowModel taskFlowModel = getTaskFlowModel(taskName, taskVersion);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public TaskFlowModel getTaskFlowModel(String taskName, long taskVersion) {
        String path = ResourceVersionHelper.buildResolvePath(TaskConstants.MODEL_TYPE_TASK, taskName, taskVersion);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel;
    }

    @Override
    public ITask parseTask(IResource resource) {
        TaskFlowModel taskFlowModel = (TaskFlowModel) new DslModelParser().parseFromResource(resource);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public ITask loadTaskFromPath(String path) {
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTask(newTaskFlowBuilder());
    }

    @Override
    public ITaskStepLib getTaskStepLib(String libName, long libVersion) {
        String path = ResourceVersionHelper.buildResolvePath(TaskConstants.MODEL_TYPE_TASK, libName, libVersion);
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTaskStepLib(newTaskStepLibBuilder());
    }

    @Override
    public ITaskStepLib loadTaskStepLibFromPath(String path) {
        TaskFlowModel taskFlowModel = (TaskFlowModel) ResourceComponentManager.instance().loadComponentModel(path);
        return taskFlowModel.getTaskStepLib(newTaskStepLibBuilder());
    }

    @Override
    public IRateLimiter getRateLimiter(ITaskRuntime taskRt, String key, double requestPerSecond, boolean global) {
        if (global) {
            String cacheKey = taskRt.getTaskName() + ":" + key;
            // plan 2514 MA-08（裁定⑤）：逐出执行位 = 同 key compute 内联（CHM bin 锁串行，
            // 无 check-then-act 窗），引用一致复核（以 existing 引用判定后整体替换），无独立清扫位
            IRateLimiter limiter = globalRateLimiters.compute(cacheKey, (k, existing) -> {
                long now = CoreMetrics.nanoTime();
                if (existing != null) {
                    long idleNs = now - existing.lastAccess;
                    if (idleNs >= TimeUnit.MILLISECONDS.toNanos(CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS.get())) {
                        // rate limiter 无在途持有者语义（acquire 即时），空闲超阈即可逐出
                        LOG.info("nop.task.global-gate-evicted:registry=rate-limiter,cacheKey={},idleMs={}",
                                k, TimeUnit.NANOSECONDS.toMillis(idleNs));
                        return new GlobalGate<>(new DefaultRateLimiter(requestPerSecond));
                    }
                    existing.lastAccess = now;
                    return existing;
                }
                warnIfOverCap(rateLimiterCapWarnAt, CFG_TASK_GLOBAL_GATE_MAX_ENTRIES.get(),
                        globalRateLimiters.size(), "global-rate-limiter");
                return new GlobalGate<>(new DefaultRateLimiter(requestPerSecond));
            }).gate;
            // 全局限流器首配置固化告警（plan 349 Phase 5，check2 P3）：同 key 二次配置不同速率时
            // 旧参数继续生效（缓存命中即返回）。W-03（CRA 8.48 R4-08，MA-10 裁定 A）：告警全局
            // 周期化（60s 间隔，复用 warnIfOverCap 同形态）——修复前每调用打印无抑制
            if (Math.abs(limiter.getPermitsPerSecond() - requestPerSecond) > 1e-9) {
                warnConfigFrozen(rateLimiterFrozenWarnAt, "rate-limiter", cacheKey,
                        "configuredPerSecond=" + requestPerSecond + ",activePerSecond=" + limiter.getPermitsPerSecond());
            }
            return limiter;
        }
        return (IRateLimiter) taskRt.computeAttributeIfAbsent("rate-limit:" + key, k -> {
            return new DefaultRateLimiter(requestPerSecond);
        });
    }

    @Override
    public ISemaphore getSemaphore(ITaskRuntime taskRt, String key, int maxPermits, boolean global) {
        if (global) {
            GlobalGate<ISemaphore> gate = globalSemaphores.compute(taskRt.getTaskName() + ":" + key, (k, existing) -> {
                long now = CoreMetrics.nanoTime();
                if (existing != null) {
                    long idleNs = now - existing.lastAccess;
                    ISemaphore sem = existing.gate;
                    // plan 2514 MA-08 逐出判据（permit 池完整性约束）：空闲超阈 且 permits 满额
                    // （无在途持有者，availablePermits==maxPermits）——在用 entry 零逐出
                    boolean permitsFull = sem.availablePermits() >= sem.maxPermits();
                    if (idleNs >= TimeUnit.MILLISECONDS.toNanos(CFG_TASK_GLOBAL_GATE_IDLE_EVICT_MS.get()) && permitsFull) {
                        LOG.info("nop.task.global-gate-evicted:registry=semaphore,cacheKey={},idleMs={}",
                                k, TimeUnit.NANOSECONDS.toMillis(idleNs));
                        return new GlobalGate<>(new DefaultSemaphore(maxPermits));
                    }
                    existing.lastAccess = now;
                    return existing;
                }
                warnIfOverCap(semaphoreCapWarnAt, CFG_TASK_GLOBAL_GATE_MAX_ENTRIES.get(),
                        globalSemaphores.size(), "global-semaphore");
                return new GlobalGate<>(new DefaultSemaphore(maxPermits));
            });
            // W-03（CRA 8.48 R4-08，MA-10 裁定 A）：semaphore 侧对称「配置固化」告警（修复前
            // 侧零信号）——同 key 二次配置不同 permits 时旧配置静默沿用，全局周期化 WARN 提示
            ISemaphore sem = gate.gate;
            if (sem.maxPermits() != maxPermits) {
                warnConfigFrozen(semaphoreFrozenWarnAt, "semaphore", taskRt.getTaskName() + ":" + key,
                        "configuredPermits=" + maxPermits + ",activePermits=" + sem.maxPermits()
                                + ",availablePermits=" + sem.availablePermits());
            }
            return sem;
        }
        return (ISemaphore) taskRt.computeAttributeIfAbsent("semaphore:" + key, k -> {
            return new DefaultSemaphore(maxPermits);
        });
    }

    /**
     * W-03（CRA 8.48 R4-08，MA-10 裁定 A）：闸门配置固化告警全局周期化（60s 间隔，2514
     * AtomicLong 形态复用）。否决 per-key 一次性去重：per-key 去重=新动态 key 无界增长面，
     * 与 2514 键空间治理同构矛盾。
     */
    private void warnConfigFrozen(AtomicLong lastWarnAt, String registry, String cacheKey, String detail) {
        long now = CoreMetrics.nanoTime();
        long last = lastWarnAt.get();
        if (now - last >= TimeUnit.MILLISECONDS.toNanos(CAP_WARN_INTERVAL_MS) && lastWarnAt.compareAndSet(last, now)) {
            LOG.warn("nop.task.global-gate-config-frozen:registry={},cacheKey={},{}; "
                            + "config frozen (global gate params require restart to take effect) "
                            + "-- WARN throttled to once per {}ms (plan 2541 W-03 MA-10)",
                    registry, cacheKey, detail, CAP_WARN_INTERVAL_MS);
        }
    }

    /**
     * plan 2514 MA-08：注册表超限 WARN 周期化（60s 间隔，WARN-once 不可操作）。
     * 上界口径 = max-entries 渐进收敛（逐出为同 key 复访内联，无全 map 清扫位），
     * 不可逐出（在用）entry 挤压超限时周期性 WARN + owner doc 包络声明承接。
     */
    private void warnIfOverCap(AtomicLong lastWarnAt, int cap, int currentSize, String registryName) {
        if (cap > 0 && currentSize >= cap) {
            long now = CoreMetrics.nanoTime();
            long last = lastWarnAt.get();
            if (now - last >= TimeUnit.MILLISECONDS.toNanos(CAP_WARN_INTERVAL_MS) && lastWarnAt.compareAndSet(last, now)) {
                LOG.warn("nop.task.global-gate-registry-over-cap:registry={},size={},configuredCap={}; "
                        + "idle eviction converges on revisit only (plan 2514 MA-08) — monitor key cardinality",
                        registryName, currentSize, cap);
            }
        }
    }

    @Override
    public Map<String, ISemaphore.SemaphoreStats> getGlobalSemaphoreStats() {
        Map<String, ISemaphore.SemaphoreStats> ret = new HashMap<>();
        globalSemaphores.forEach((k, gate) -> {
            ret.put(k, gate.gate.getStats());
        });
        return ret;
    }

    @Override
    public Map<String, IRateLimiter.RateLimiterStats> getGlobalRateLimiterStats() {
        Map<String, IRateLimiter.RateLimiterStats> ret = new HashMap<>();
        globalRateLimiters.forEach((k, gate) -> {
            ret.put(k, gate.gate.getStats());
        });
        return ret;
    }

    @Override
    public void resetGlobalStats() {
        globalSemaphores.forEach((k, gate) -> {
            gate.gate.resetStats();
        });
        // 补充限流器统计重置（plan 349 Phase 5，修复 check2 P3）：
        // 与 getGlobalRateLimiterStats 成对，修复前运维重置后限流器统计无法清零
        globalRateLimiters.forEach((k, gate) -> {
            gate.gate.resetStats();
        });
    }

    protected ITaskFlowBuilder newTaskFlowBuilder() {
        return new TaskFlowBuilder();
    }

    protected ITaskStepLibBuilder newTaskStepLibBuilder() {
        return new TaskStepLibBuilder();
    }
}