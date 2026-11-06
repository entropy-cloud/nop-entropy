/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.api.debugger.DebugValueKey;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.ThreadInfo;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.LocalCache;
import io.nop.commons.collections.LongHashMap;
import io.nop.commons.concurrent.thread.NamedThreadFactory;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.debugger.session.ThreadLocalReplDeadlineChannel;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.compile.LexicalScopeAnalysis;
import io.nop.xlang.expr.simple.SimpleExprParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.nop.commons.cache.CacheConfig.newConfig;
import static io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_ALREADY_CLOSED;

public class XLangDebugger extends BreakpointManagerImpl implements IXLangDebugger {
    static final Logger LOG = LoggerFactory.getLogger(XLangDebugger.class);

    private volatile boolean breakpointsMuted;

    private volatile DebugStepMode stepMode = DebugStepMode.RESUME;
    private final LongHashMap<SuspendedThread> suspendedThreads = new LongHashMap<>();

    private final ICache<String, IExecutableExpression> compileCache = LocalCache.newCache("debug-expr-cache",
            newConfig(100), this::compileExpr);

    private volatile SuspendedThread lastSuspendThread; //NOSONAR

    private volatile Breakpoint tempBreakpoint; //NOSONAR

    private volatile boolean suspended;
    private volatile boolean closed;

    /**
     * 会话租约截止时间（epoch ms）；0 = 租约禁用（默认会话/IDEA 场景，永不过期）。
     * 时钟所有权：网关（item 12）经 renewLease 推进（D2 §3.7 职责分界）。
     */
    private volatile long leaseDeadlineMillis;

    /**
     * suspend() 的会话级一次生效旗标（D3 §3.6 suspendOnce）：下一检查点挂起该线程并清除。
     * CAS 清除保证两线程同到检查点仅挂起一个。
     */
    private final AtomicBoolean suspendOnce = new AtomicBoolean(false);

    /**
     * stepX 无挂起目标错误码（已迁入 XLangErrors——plan 2471 additive 批次，D1 §3.7 表）。
     * 保留同名字段作为兼容引用。
     */
    public static final ErrorCode ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED = io.nop.xlang.XLangErrors.ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED;

    private int monitorWaitInterval = 200;
    private final ReentrantLock monitorLock = new ReentrantLock();
    private final Condition resumeCondition = monitorLock.newCondition();
    private final Condition suspendedCondition = monitorLock.newCondition();

    private volatile IDebugNotifier notifier;

    private ScheduledExecutorService cleanupThread = Executors
            .newSingleThreadScheduledExecutor(NamedThreadFactory.newThreadFactory("xlang-debugger-cleanup", true));

    enum DebugStepMode {
        SUSPEND, RESUME, STEP_INTO, STEP_OVER, STEP_OUT
    }

    public XLangDebugger() {
        cleanupThread.schedule(this::cleanup, 30, TimeUnit.SECONDS);
    }

    /**
     * 会话销毁收尾（plan 2479 C-2，item 24 审计）：释放调度线程但**不置 closed、不改
     * 租约/挂起语义**（D2 §3.3 红线：graceful 收尾 = resume 语义，区别于 close 的
     * abort 语义）。幂等（shutdownNow 可重入）；cleanup 为一次性 30s 任务，dispose 后
     * 残留 SuspendedThread 条目随实例 GC，无泄漏。
     */
    public void dispose() {
        cleanupThread.shutdownNow();
    }

    @Override
    public void close() {
        closed = true;
        cleanupThread.shutdown();
        tempBreakpoint = null;
        // close 语义（abort）：signal 唤醒挂起线程 → 谓词见 closed 且无局部放行 → 抛
        // ALREADY_CLOSED（出口判定顺序见 monitorWait）。不置局部放行。
        monitorLock.lock();
        try {
            resumeCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
        synchronized (suspendedThreads) {
            suspendedThreads.clear();
        }
    }

    private void cleanup() {
        synchronized (suspendedThreads) {
            Iterator<SuspendedThread> it = suspendedThreads.values().iterator();
            while (it.hasNext()) {
                if (!it.next().isAlive()) {
                    it.remove();
                }
            }
        }
    }

    public int getMonitorWaitInterval() {
        return monitorWaitInterval;
    }

    public void setMonitorWaitInterval(int value) {
        this.monitorWaitInterval = value;
    }

    public IDebugNotifier getNotifier() {
        return notifier;
    }

    public void setNotifier(IDebugNotifier notifier) {
        this.notifier = notifier;
    }

    /**
     * 调试器是否已关闭（close 语义后为 true）。会话生命周期（item 9）与
     * 停机清扫断言使用。
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isSuspended() {
        return suspended;
    }

    @Override
    public void waitSuspended() {
        monitorLock.lock();
        try {
            while (!suspended) {
                if (closed)
                    return;
                try {
                    if (!suspendedCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) {
                        LOG.trace("nop.debugger.suspend-await-timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 中断后继续死等会永久占用RPC工作线程，退出等待由调用方决定重试
                    return;
                }
            }
        } finally {
            monitorLock.unlock();
        }
    }

    /**
     * stepX 定向重建（D3 §3.6）：目标 = 处理时刻快照的 lastSuspendThread；仅置目标线程
     * 局部 step 模式与放行标志（monitorLock 内，与挂起入口复位同锁串行化）；signalAll 后
     * 目标线程谓词满足退出，其余挂起线程重查自身谓词继续等待。stepX 全程零事件
     * （D2 §3.3：释放-再挂起由 evt.suspended 承载，防 UI 双跳变）。
     */
    private void releaseTarget(DebugStepMode stepMode) {
        monitorLock.lock();
        try {
            // 处理时刻快照判定（D3 §3.6.5）：无已挂起目标即报错（含"已释放未再挂起"瞬态
            // 窗口）。plan 2479 C-4：快照读与 isSuspended 复核同移 monitorLock 内（对齐
            // releaseThread/releaseAllThreads 锁内形态）——消除快照-加锁窗口的 stepX 静默
            // 丢失/陈旧 step 模式施用（item 24 审计 C-4 交错 A/B）
            SuspendedThread target = this.lastSuspendThread;
            if (target == null || !target.isSuspended()) {
                LOG.info("nop.debugger.step-no-suspended-thread");
                throw new NopException(ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED);
            }
            target.setThreadStepMode(stepMode);
            target.setLocalReleased(true);
            resumeCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
    }

    @Override
    public void stepInto() {
        releaseTarget(DebugStepMode.STEP_INTO);
    }

    @Override
    public void stepOver() {
        releaseTarget(DebugStepMode.STEP_OVER);
    }

    @Override
    public void stepOut() {
        releaseTarget(DebugStepMode.STEP_OUT);
    }

    /**
     * suspend() 重建（D3 §3.6.7）：会话级 suspendOnce 旗标，任一下一检查点挂起该线程并
     * 清除（一次生效，CAS 原子）。
     */
    @Override
    public void suspend() {
        suspendOnce.set(true);
        monitorLock.lock();
        try {
            suspendedCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
    }

    /**
     * 用户 resume（D1 evt.resumed cause=user）：全部挂起线程局部放行 + 清局部 step 状态；
     * 不置 closed 不抛错。与 evt.leaseExpired 互斥（D1 §3.5）。
     */
    @Override
    public void resume() {
        List<Long> released = releaseAllThreads();
        if (!released.isEmpty()) {
            IDebugNotifier cb = notifier;
            if (cb != null)
                cb.notifyResumed(released, "user");
        }
    }

    /**
     * releaseAll（D2 §3.3 宽限路径原语）：resume 语义全量放行——置局部放行、清局部 step
     * 状态、signalAll；不置 closed 不抛错。聚合发 notifyLeaseExpired（D2 §3.6：宽限路径
     * 载体，调用方 item 12 网关不再重复发）。
     */
    public void releaseAll() {
        List<Long> released = releaseAllThreads();
        if (!released.isEmpty()) {
            IDebugNotifier cb = notifier;
            if (cb != null)
                cb.notifyLeaseExpired(released);
        }
    }

    private List<Long> releaseAllThreads() {
        List<Long> released = new ArrayList<>();
        monitorLock.lock();
        try {
            synchronized (suspendedThreads) {
                for (SuspendedThread thread : suspendedThreads.values()) {
                    if (thread.isSuspended()) {
                        thread.setLocalReleased(true);
                        thread.setThreadStepMode(DebugStepMode.RESUME);
                        released.add(thread.getThreadId());
                    }
                }
            }
            resumeCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
        return released;
    }

    /**
     * 定向唤醒原语（D3 §3.6.3 行为契约）：置目标线程局部放行标志 → signalAll → 目标线程
     * 谓词满足退出挂起；其余挂起线程重查自身谓词继续等待；对不存在/未挂起的 threadId
     * no-op（幂等）。不发任何事件（stepX 消费）。
     */
    public void releaseThread(long threadId) {
        monitorLock.lock();
        try {
            SuspendedThread thread = getSuspendedThread(threadId);
            if (thread != null && thread.isSuspended())
                thread.setLocalReleased(true);
            resumeCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
    }

    /**
     * 租约续期（D2 §3.7）：推进会话租约截止时间（now + ttl）。仅推进，不改变挂起态。
     */
    public void renewLease(long ttlMillis) {
        this.leaseDeadlineMillis = CoreMetrics.currentTimeMillis() + ttlMillis;
    }

    /**
     * 租约过期戳（宽限销毁收尾，D2 §3.1①②）：置 leaseDeadline=now，此后新命中断点
     * 走授予判定②"租约已过期不挂起"直接继续执行。不改变挂起态、不发事件
     * （放行事件由 releaseAll 聚合发）——补 releaseAll 不覆盖的嵌套命中面：
     * 宽限销毁后仍在执行中的脚本线程继续命中断点不得再挂起（无观察者）。
     */
    public void expireLease() {
        this.leaseDeadlineMillis = CoreMetrics.currentTimeMillis();
    }

    public long getLeaseDeadlineMillis() {
        return leaseDeadlineMillis;
    }

    public List<Long> getSuspendedThreadIds() {
        synchronized (suspendedThreads) {
            List<Long> ids = new ArrayList<>(suspendedThreads.size());
            for (SuspendedThread thread : suspendedThreads.values()) {
                if (thread.isSuspended())
                    ids.add(thread.getThreadId());
            }
            return ids;
        }
    }

    private boolean leaseExpired() {
        long deadline = leaseDeadlineMillis;
        return deadline > 0 && CoreMetrics.currentTimeMillis() >= deadline;
    }

    @Override
    public void muteBreakpoints(boolean muted) {
        this.breakpointsMuted = muted;
    }

    @Override
    public void updateBreakpoints(List<Breakpoint> bps, boolean muted) {
        setBreakpoints(bps);
        muteBreakpoints(muted);
    }

    @Override
    public boolean isBreakpointsMuted() {
        return breakpointsMuted;
    }

    @Override
    public List<ThreadInfo> getSuspendedThreads() {
        synchronized (suspendedThreads) {
            List<ThreadInfo> ret = new ArrayList<>(suspendedThreads.size());
            for (SuspendedThread thread : suspendedThreads.values()) {
                if (thread.isSuspended()) {
                    ret.add(thread.getThreadInfo());
                }
            }
            return ret;
        }
    }

    @Override
    public void runToPosition(Breakpoint bp) {
        this.tempBreakpoint = bp;
        this.resume();
    }

    SuspendedThread getSuspendedThread(long threadId) {
        SuspendedThread thread;
        synchronized (suspendedThreads) {
            thread = suspendedThreads.get(threadId);
        }
        if (thread == null) {
            LOG.info("nop.debugger.not-find-suspended-thread:threadId={}", threadId);
        }
        return thread;
    }

    IExecutableExpression compileExpr(String expr) {
        Expression expression = SimpleExprParser.newDefault().parseExpr(null, expr);
        IXLangCompileScope context = XLang.newXplCompiler().newCompileScope();
        context.setAllowUnregisteredScopeVar(true);
        new LexicalScopeAnalysis(context).analyze(expression);
        return new DebugExecutableBuilder().processAST(expression, context);
    }

    IExecutableExpression getCompiledExpr(String expr) {
        return compileCache.get(expr);
    }

    @Override
    public DebugVariable getExprValue(long threadId, int frameIndex, String expr) {
        Object value = eval(threadId, frameIndex, expr);
        DebugVariable var = DebugValueHelper.buildDebugVariable(expr, value, null);
        var.setKind("expr");
        return var;
    }

    private Object eval(long threadId, int frameIndex, String expr) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            return null;

        IExecutableExpression exec = getCompiledExpr(expr);
        if (exec == null)
            return null;

        synchronized (thread) {
            // plan 2479 C-5：放行瞬态窗口（条目仍在 map、isSuspended=false）拒绝读——
            // 引擎兜底取代网关"良性竞态"注释性豁免（item 24 审计 C-5）
            if (!thread.isSuspended())
                throw new NopException(ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED);
            EvalRuntime rt = thread.getEvalRuntime();
            // plan 2478 SEC-07：watch 求值入口消费 REPL deadline 通道（无窗口线程
            // checkDeadline 早退不误伤；窗口内超时/取消抛 ReplCancelledError——Error 系，
            // 由网关 evalWithDeadline 显式捕获映射错误响应）。打断双层 = 本入口检查 +
            // 表达式内函数调用边界经全局执行器的 per-entry 检查
            ThreadLocalReplDeadlineChannel.instance().checkDeadline(rt);
            return exec.execute(DefaultExpressionExecutor.INSTANCE, rt.getRuntimeForFrame(frameIndex));
        }
    }

    @Override
    public List<DebugVariable> expandExprValue(long threadId, int frameIndex, String expr, List<DebugValueKey> keys) {
        Object value = eval(threadId, frameIndex, expr);
        if (value == null)
            return Collections.emptyList();

        return DebugValueHelper.getExpandValue(value, keys);
    }

    @Override
    public StackInfo getStackInfo(long threadId) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            return null;
        synchronized (thread) {
            if (!thread.isSuspended())
                throw new NopException(ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED);
            return thread.getStackInfo();
        }
    }

    @Override
    public List<DebugVariable> getScopeVariables(long threadId) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            return Collections.emptyList();
        synchronized (thread) {
            if (!thread.isSuspended())
                throw new NopException(ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED);
            return thread.getScopeVariables(thread.getEvalRuntime());
        }
    }

    @Override
    public List<DebugVariable> getFrameVariables(long threadId, int frameIndex) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            // 接口契约未声明@Nullable，客户端按非null消费（线程已恢复等场景返回空列表）
            return Collections.emptyList();
        synchronized (thread) {
            if (!thread.isSuspended())
                throw new NopException(ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED);
            if (frameIndex == 0) {
                List<DebugVariable> vars = thread.getScopeVariables(thread.getEvalRuntime());
                List<DebugVariable> frameVars = thread.getFrameVariables(thread.getEvalRuntime(), frameIndex);
                vars.addAll(frameVars);
                return vars;
            }
            return thread.getFrameVariables(thread.getEvalRuntime(), frameIndex);
        }
    }

    @Override
    public void checkBreakpoint(SourceLocation loc, EvalRuntime rt) {
        // 授予判定①（D2 §3.1①）：closed/销毁中 → 不挂起直接继续（不抛错——抛错保留给
        // "挂起中被 close 中止"路径；此处跳过挂起保证脚本执行完）
        if (closed)
            return;
        // 授予判定②（D2 §3.1②）：租约已过期 → 无观察者，不挂起直接继续
        if (leaseExpired()) {
            LOG.warn("nop.debugger.lease-expired-skip-breakpoint:loc={}", loc);
            return;
        }
        // suspendOnce（D3 §3.6.7）：一次生效，CAS 原子清除（两线程同到仅挂起一个）
        if (suspendOnce.compareAndSet(true, false)) {
            checkSuspend(loc, rt);
            return;
        }
        if (loc == null || breakpointsMuted)
            return;

        if (!checkTempBreakpoint(loc, rt)) {
            DebugStepMode stepMode = currentThreadStepMode();
            switch (stepMode) {
                case SUSPEND: {
                    checkSuspend(loc, rt);
                    break;
                }
                case STEP_INTO: {
                    checkStepInto(loc, rt);
                    break;
                }
                default: {
                    if (!checkHitBreakpoint(loc, rt)) {
                        switch (stepMode) {
                            case STEP_OVER: {
                                checkStepOver(loc, rt);
                                break;
                            }
                            case STEP_OUT: {
                                checkStepOut(loc, rt);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 当前线程的 step 判定模式（per-thread 局部化，D2 §3.3）：非挂起线程（map 无条目）
     * 视为 RESUME。
     */
    private DebugStepMode currentThreadStepMode() {
        SuspendedThread thread = getSuspendedThread(Thread.currentThread().getId());
        return thread == null ? DebugStepMode.RESUME : thread.getThreadStepMode();
    }

    private void checkStepInto(SourceLocation loc, EvalRuntime rt) {
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.setThreadStepMode(DebugStepMode.RESUME);
        thread.update(loc, rt);

        if (notifier != null) {
            notifier.notifyStepInto(thread, loc, rt);
        }
        doSuspend(thread);
    }

    private boolean checkTempBreakpoint(SourceLocation loc, EvalRuntime rt) {
        if (!matchTempBreakpoint(loc, rt))
            return false;
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.setThreadStepMode(DebugStepMode.RESUME);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread);
        return true;
    }

    private void checkSuspend(SourceLocation loc, EvalRuntime rt) {
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.setThreadStepMode(DebugStepMode.RESUME);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifySuspend(thread, loc, rt);
        }
        doSuspend(thread);
    }

    private void checkStepOver(SourceLocation loc, EvalRuntime rt) {
        SuspendedThread thread = getSuspendedThread(Thread.currentThread().getId());
        if (thread == null) {
            // 不是挂起线程直接忽略
            return;
        }

        int lastFrame = thread.getLastBreakFrameIndex();
        int currentFrame = thread.getCurrentMaxFrameIndex();

        SourceLocation lastLoc = thread.getLastBreakLocation();
        if (lastLoc == null || !lastLoc.isSameLine(loc)) {
            if (currentFrame <= lastFrame) {
                thread.setThreadStepMode(DebugStepMode.RESUME);
                thread.update(loc, rt);
                if (notifier != null) {
                    notifier.notifyStepOver(thread, loc, rt);
                }
                doSuspend(thread);
            }
        }
    }

    private void checkStepOut(SourceLocation loc, EvalRuntime rt) {
        SuspendedThread thread = getSuspendedThread(Thread.currentThread().getId());
        if (thread == null) {
            // 不是挂起线程直接忽略
            return;
        }

        int lastFrame = thread.getLastBreakFrameIndex();
        int currentFrame = thread.getCurrentMaxFrameIndex();
        if (lastFrame > currentFrame) {
            thread.setThreadStepMode(DebugStepMode.RESUME);

            thread.update(loc, rt);
            if (notifier != null) {
                notifier.notifyStepOut(thread, loc, rt);
            }
            doSuspend(thread);
        }
    }

    private boolean checkHitBreakpoint(SourceLocation loc, EvalRuntime rt) {
        Breakpoint bp = getBreakpointAt(loc);
        if (bp == null)
            return false;

        SuspendedThread thread = makeSuspendedThread(rt);
        SourceLocation lastLoc = thread.getLastBreakLocation();
        // 点击resume之后不会直接停在同一行的断点上
        if (lastLoc != null && lastLoc.isSameLine(loc))
            return false;

        if (bp.getCondition() != null) {
            if (!matchCondition(bp.getCondition(), rt)) {
                return false;
            }
        }

        if (logBreakpoint(bp, rt))
            return false;

        thread.setThreadStepMode(DebugStepMode.RESUME);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread);
        return true;
    }

    private void doSuspend(SuspendedThread thread) {
        // per-thread 发布并入 monitorWait 锁内临界区（item 10 审计 F4）：放行原语以
        // isSuspended() 为闸门，闸门翻转与 localReleased 复位必须同锁串行化——否则
        // 锁外窗口内到达的放行会被入口复位抹掉（放行已上报但线程继续挂起）。
        // notifier 事件已在调用方（check* 方法）于锁外发出。
        lastSuspendThread = thread;
        monitorWait(thread);
    }

    private boolean matchTempBreakpoint(SourceLocation loc, EvalRuntime rt) {
        Breakpoint bp = this.tempBreakpoint;
        if (bp == null)
            return false;

        if (bp.getLine() != loc.getLine()) {
            return false;
        }

        if (!bp.getSourcePath().equals(toSourcePath(loc)))
            return false;

        if (bp.getCondition() != null) {
            if (!matchCondition(bp.getCondition(), rt)) {
                return false;
            }
        }

        // 清除一次性的临时断点
        tempBreakpoint = null;

        if (logBreakpoint(bp, rt)) {
            // 仅打印log信息，不需要停止程序执行
            return false;
        }

        // 匹配到断点，需要停止后续执行
        return true;
    }

    private boolean logBreakpoint(Breakpoint bp, EvalRuntime rt) {
        if (bp.getLogExpr() != null) {
            try {
                String message = getExprString(bp.getLogExpr(), rt);
                LOG.info("nop.debugger.log:message={},expr={},sourcePath={},line={}", message, bp.getLogExpr(),
                        bp.getSourcePath(), bp.getLine());
                if (notifier != null) {
                    notifier.notifyLog(makeSuspendedThread(rt), bp.getSourcePath(), bp.getLine(), message);
                }
            } catch (Exception e) {
                LOG.warn("nop.debugger.eval-expr-fail", e);
            }

            // 仅打印log信息，不需要停止程序执行
            return true;
        }

        return false;
    }

    /**
     * 挂起等待（D2 §3.3 谓词局部化）：循环谓词为 per-thread 组合
     * `!closed && !localReleased && !leaseExpired()`；signalAll 唤醒后各线程重查自身谓词。
     * 出口判定顺序（钉死，消 flaky）：局部放行/租约到期优先正常返回；仅 closed 且无局部
     * 放行 → 抛 ALREADY_CLOSED（close 语义）。
     * <p>标志生命周期（M2）：localReleased 在本方法入口于 monitorLock 内复位（与
     * releaseAll/releaseThread/stepX 同锁串行化，防"放行后被入口复位抹掉"竞态）。
     */
    private void monitorWait(SuspendedThread thread) {
        boolean leaseExpiredExit = false;
        monitorLock.lock();
        try {
            // 挂起入口复位放行标志 + per-thread 发布（同锁临界区——M2/F4 时序点）
            thread.setLocalReleased(false);
            // plan 2479 C-4 加固：新挂起 = 上一次 step 模式已消费——复位防陈旧 step 模式
            // 经线程 ID 回收/旧 map 条目跨执行污染（漏断点/幽灵挂起，item 24 审计 C-4 交错 B）
            thread.setThreadStepMode(DebugStepMode.RESUME);
            thread.setSuspended(true);
            suspended = true;
            suspendedCondition.signalAll();
            while (!closed && !thread.isLocalReleased() && !leaseExpired()) {
                if (resumeCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) {
                    LOG.trace("nop.debugger.monitor-await-timeout");
                }
            }
            leaseExpiredExit = !thread.isLocalReleased() && leaseExpired();
        } catch (InterruptedException e) {
            // ignore
            Thread.currentThread().interrupt();
        } finally {
            thread.setSuspended(false);
            if (!hasSuspendedThreads())
                suspended = false;
            monitorLock.unlock();
        }
        // 出口判定顺序：局部放行/租约到期优先正常返回（即使 closed 并存——宽限 releaseAll
        // 与 close 的竞态按放行处理）；仅 closed 且无局部放行 → close 语义中止
        if (closed && !thread.isLocalReleased() && !leaseExpiredExit) {
            throw new NopEvalException(ERR_XLANG_DEBUGGER_ALREADY_CLOSED);
        }
        if (leaseExpiredExit) {
            // 租约到期自退出：per-thread 单元素事件（D1 evt.leaseExpired 载体；宽限聚合
            // 路径由 releaseAll 聚合发，互不重复——本线程经谓词到期退出不经 localReleased）
            IDebugNotifier cb = notifier;
            if (cb != null)
                cb.notifyLeaseExpired(java.util.List.of(thread.getThreadId()));
        }
    }

    private boolean hasSuspendedThreads() {
        synchronized (suspendedThreads) {
            for (SuspendedThread thread : suspendedThreads.values()) {
                if (thread.isSuspended())
                    return true;
            }
            return false;
        }
    }

    private SuspendedThread makeSuspendedThread(EvalRuntime rt) {
        Thread currentThread = Thread.currentThread();
        synchronized (suspendedThreads) {
            if (closed)
                throw new NopException(ERR_XLANG_DEBUGGER_ALREADY_CLOSED);

            SuspendedThread thread = suspendedThreads.get(currentThread.getId());
            if (thread == null) {
                thread = new SuspendedThread(currentThread, rt, this::toSourcePath);
                suspendedThreads.put(currentThread.getId(), thread);
            }
            return thread;
        }
    }

    private boolean matchCondition(String condition, EvalRuntime rt) {
        try {
            return ConvertHelper.toTruthy(evaluate(condition, rt));
        } catch (Exception e) {
            LOG.warn("nop.debugger.eval-condition-fail", e);
            return false;
        }
    }

    private String getExprString(String expr, EvalRuntime rt) {
        return ConvertHelper.toString(evaluate(expr, rt));
    }

    private Object evaluate(String expr, EvalRuntime rt) {
        IExecutableExpression exec = getCompiledExpr(expr);
        return exec.execute(DefaultExpressionExecutor.INSTANCE, rt);
    }
}