package io.nop.task.impl;

import io.nop.api.core.util.ICancelToken;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskStateStore;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskConstants;
import io.nop.task.utils.TaskStepHelper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskStepRuntimeImpl implements ITaskStepRuntime {
    static final Logger LOG = LoggerFactory.getLogger(TaskStepRuntimeImpl.class);

    /**
     * plan 364 [维度05-06] 访问约束不变式（plan 349 Phase 4 的延续）：
     * 除 {@code cancelToken}/{@code stepState}（volatile）与 {@code stepCleanups}
     * （并发队列，plan 2541 W-11 MA-20 裁定 C (b)）外，其余可变字段
     * （outputNames/exception/persistVars/enabledFlags/recoverMode）的写
     * 必须发生在 step 创建线程或经 future 链的同步点（thenCompose/whenComplete 的
     * happens-before）传递；新增不经 future 链的跨线程读写时必须显式加同步。
     */

    /**
     * task 级 attribute key：记录本次 task 执行内已实例化过的 stepPath 集合。
     * 用于把 loadStepState（resume 语义）限定在每个 stepPath 的首次实例化上。
     */
    private static final String ATTR_FIRST_INSTANTIATED_STEP_PATHS = "nop.task.first-instantiated-step-paths";

    private final ITaskRuntime taskRt;
    private final ITaskStateStore stateStore;
    private final IEvalScope scope;
    // volatile（plan 349 Phase 4）：执行线程写（withCancellable/Timeout wrapper 设置/恢复），
    // 异步回调线程/超时定时器线程经 isCancelled() 读，需要跨线程可见性
    private volatile ICancelToken cancelToken;
    private Set<String> outputNames;
    // plan 364 [维度05-06]：跨线程可见性收敛——stepState 是全部默认读方法（getStepPath/getRunId 等
    // 经 getState()）的根，定时器/异步回调线程可能不经 future 链读取；exception 由包装层跨线程写
    private volatile ITaskStepState stepState;

    private Set<String> enabledFlags = Collections.emptySet();
    private boolean recoverMode;
    private Set<String> persistVars;

    // W-11（CRA 8.48 R4-08，MA-20 裁定 C 三选一 (b)）：无锁并发容器 + 单消费者 drain——
    // 与 TaskRuntimeImpl.cleanups（Cancellable 正例）防护水平对齐，公开扩展点生产者出现即
    // 触达安全（synchronized 写 × 非同步 drain 的不对称锁纪律已消除）
    private final Queue<Runnable> stepCleanups = new ConcurrentLinkedQueue<>();
    private Throwable exception;

    // W-04（CRA 8.48 R4-08，MA-11 裁定 A）：throttle 重入检测执行链持有键集（impl 内部载体，
    // 不入 ITaskStepRuntime 公共契约）。held 集=同任务祖先链/自身判定（raw key）；heldGlobal 集=
    // CallTaskStep 跨任务 inherited 快照取材（仅 global 键跨任务共享 permit 池）。惰性创建。
    private Set<String> throttleHeldKeys;
    private Set<String> throttleHeldGlobalKeys;
    // 同任务内父 stepRt 引用（newStepRuntime 建立子 runtime 时赋值；main stepRt 恒 null——链终止）
    private TaskStepRuntimeImpl parentStepRt;

    // W-04 键集访问面：public 于 impl 类（ITaskStepRuntime 公共契约零变化——载体为 impl
    // 内部字段，仅 TaskStepHelper 经 instanceof 调用）
    public Set<String> peekThrottleHeldKeys() {
        return throttleHeldKeys;
    }

    public Set<String> peekThrottleHeldGlobalKeys() {
        return throttleHeldGlobalKeys;
    }

    public void addThrottleHeldKey(String key, boolean global) {
        if (throttleHeldKeys == null)
            throttleHeldKeys = ConcurrentHashMap.newKeySet();
        throttleHeldKeys.add(key);
        if (global) {
            if (throttleHeldGlobalKeys == null)
                throttleHeldGlobalKeys = ConcurrentHashMap.newKeySet();
            throttleHeldGlobalKeys.add(key);
        }
    }

    public void removeThrottleHeldKey(String key, boolean global) {
        if (throttleHeldKeys != null)
            throttleHeldKeys.remove(key);
        if (global && throttleHeldGlobalKeys != null)
            throttleHeldGlobalKeys.remove(key);
    }

    public TaskStepRuntimeImpl getParentStepRt() {
        return parentStepRt;
    }

    public TaskStepRuntimeImpl(ITaskRuntime taskRt, ITaskStateStore stateStore, IEvalScope scope) {
        this.taskRt = taskRt;
        this.stateStore = stateStore;
        this.scope = scope;
        this.scope.setLocalValue(TaskConstants.VAR_TASK_STEP_RT, this);
    }

    @Override
    public IEvalScope getEvalScope() {
        return scope;
    }

    @Override
    public ITaskRuntime getTaskRuntime() {
        return taskRt;
    }

    @Override
    public ICancelToken getCancelToken() {
        return cancelToken;
    }

    @Override
    public void setCancelToken(ICancelToken cancelToken) {
        this.cancelToken = cancelToken;
    }

    @Override
    public Set<String> getOutputNames() {
        return outputNames;
    }

    @Override
    public void setOutputNames(Set<String> outputNames) {
        this.outputNames = outputNames;
    }

    @Override
    public ITaskStepState getState() {
        return stepState;
    }

    public void setState(ITaskStepState stepState) {
        this.stepState = stepState;
    }

    @Override
    public void saveState() {
        stateStore.saveStepState(this);
    }

    @Nonnull
    @Override
    public Set<String> getTagSet() {
        return enabledFlags;
    }

    @Override
    public void setTagSet(Set<String> enabledFlags) {
        this.enabledFlags = enabledFlags == null ? Collections.emptySet() : enabledFlags;
    }

    @Override
    public boolean isSupportPersist() {
        return stateStore.isSupportPersist();
    }

    @Override
    public boolean isRecoverMode() {
        return recoverMode;
    }

    public void setRecoverMode(boolean recoverMode) {
        this.recoverMode = recoverMode;
    }

    public Set<String> getPersistVars() {
        return persistVars;
    }

    public void setPersistVars(Set<String> persistVars) {
        this.persistVars = persistVars;
    }

    @Override
    public ITaskStepRuntime newStepRuntime(String stepName, String stepType,
                                           Set<String> persistVars, boolean useParentScope, boolean concurrent) {
        IEvalScope baseScope = useParentScope ? scope : getTaskRuntime().getEvalScope();
        IEvalScope childScope = baseScope.newChildScope(true, concurrent);
        TaskStepRuntimeImpl newStepRt = new TaskStepRuntimeImpl(taskRt, stateStore, childScope);

        ITaskStepState newState = null;
        // loadStepState（resume 语义）只对本次 task 执行内该 stepPath 的首次实例化生效。
        // 同一执行内相同 stepPath 的后续实例化（loop 迭代 2+、fork/forkN 分支 2+ 共用同一
        // stepPath）必须使用全新 state 正常执行，否则 continuation-skip 会命中首轮持久化的
        // 终态行，静默跳过后续迭代/分支并复用首轮结果（loadStepState 仅按 taskInstanceId +
        // stepPath 定位，无法区分迭代）。每次重新执行（resume / re-execution）都会构建新的
        // task runtime，其 attribute 集合为空，故唯一路径步骤的 resume-by-load 语义不受影响。
        if (isFirstInstantiation(stepName)) {
            newState = stateStore.loadStepState(stepState, stepName, stepType, taskRt);
        }
        if (newState != null) {
            newStepRt.setRecoverMode(true);
        } else {
            newState = stateStore.newStepState(stepState, stepName, stepType, taskRt);
        }
        newStepRt.setState(newState);
        newStepRt.setPersistVars(persistVars);
        newStepRt.setCancelToken(cancelToken);
        // W-04（CRA 8.48 R4-08，MA-11 裁定 A）：同任务内父链引用——throttle 重入检测沿
        // parentStepRt 上溯遍历祖先 held 集；fork 分支各有独立子 stepRt（AbstractForkTaskStep
        // executeFork 每分支独立 newStepRuntime），兄弟分支 held 集天然隔离
        newStepRt.parentStepRt = this;
        return newStepRt;
    }

    /**
     * 记录 stepPath 并返回它是否是本次 task 执行内的首次实例化。
     * fork 分支可能并发实例化同名步骤，使用并发集合保证 add 的原子性。
     */
    @SuppressWarnings("unchecked")
    private boolean isFirstInstantiation(String stepName) {
        Set<String> instantiated = (Set<String>) taskRt.computeAttributeIfAbsent(
                ATTR_FIRST_INSTANTIATED_STEP_PATHS, key -> ConcurrentHashMap.newKeySet());
        String parentPath = stepState == null ? null : stepState.getStepPath();
        return instantiated.add(TaskStepHelper.buildStepPath(parentPath, stepName));
    }

    @Override
    public void addStepCleanup(Runnable cleanup) {
        if (cleanup == null)
            return;
        // W-11（MA-20 裁定 C (b)）：offer 原子（synchronized 已移除——写×drain 对称无锁化）
        stepCleanups.offer(cleanup);
    }

    @Override
    public void runStepCleanups() {
        // W-11（MA-20 裁定 C (b)）：单消费者 drain（poll 循环至空）——与并发 offer 无锁安全
        Runnable cleanup;
        while ((cleanup = stepCleanups.poll()) != null) {
            runCleanup(cleanup);
        }
    }

    private void runCleanup(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Exception e) {
            LOG.error("nop.err.xpt.run-cleanup-error", e);
        }
    }

    @Override
    public Throwable getException() {
        return exception;
    }

    @Override
    public void setException(Throwable exception) {
        this.exception = exception;
    }
}