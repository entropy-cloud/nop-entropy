/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
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
     * resume 纪元（CRA 8.40 W-04）：由 resume 侧（monitorNotify，monitorLock 内）推进的单调计数。
     * 六停点序列在 notifier 回调（宣告）之前取得快照交给 doSuspend；等待判据为「纪元仍等于快照且
     * 未 closed」——快照之后到达的任何 resume（包括 notifier 回调内同步投递的 resume）都使本停点
     * 直接完成，不进入等待；快照之前到达的 resume 属于上一停点或无停点，对本停点无效。写入在
     * monitorLock 内、读取为 volatile 读，快照跨线程可见。
     */
    private volatile long resumeEpoch;

    private int monitorWaitInterval = 200;
    private final ReentrantLock monitorLock = new ReentrantLock();
    private final Condition resumeCondition = monitorLock.newCondition();
    private final Condition suspendedCondition = monitorLock.newCondition();

    private IDebugNotifier notifier;

    private ScheduledExecutorService cleanupThread = Executors
            .newSingleThreadScheduledExecutor(NamedThreadFactory.newThreadFactory("xlang-debugger-cleanup", true));

    enum DebugStepMode {
        SUSPEND, RESUME, STEP_INTO, STEP_OVER, STEP_OUT
    }

    /**
     * 缺省清理初始延迟与周期（毫秒，CRA 8.40 W-06：与原一次性 30s 节拍一致）。
     */
    static final long DEFAULT_CLEANUP_INITIAL_DELAY_MILLIS = 30_000;
    static final long DEFAULT_CLEANUP_DELAY_MILLIS = 30_000;

    private final long cleanupInitialDelayMillis;
    private final long cleanupDelayMillis;

    public XLangDebugger() {
        this(DEFAULT_CLEANUP_INITIAL_DELAY_MILLIS, DEFAULT_CLEANUP_DELAY_MILLIS);
    }

    /**
     * 清理初始延迟/周期注入构造器（CRA 8.40 测试缝 S3，包内可见）：调度发生在构造期，setter 无法
     * 改写已提交的调度——注入点只能是构造器（monitorWaitInterval setter 为「可注入参数」先例，
     * 形态因构造期调度而取构造器）。生产调用面走无参构造器，零改动。
     */
    XLangDebugger(long cleanupInitialDelayMillis, long cleanupDelayMillis) {
        this.cleanupInitialDelayMillis = cleanupInitialDelayMillis;
        this.cleanupDelayMillis = cleanupDelayMillis;
        // 固定延迟周期（W-06：原一次性 schedule 为笔误）：cleanup 持 suspendedThreads 监视器遍历，
        // 慢执行时不得补偿性连发。
        cleanupThread.scheduleWithFixedDelay(this::cleanupPeriodic, cleanupInitialDelayMillis,
                cleanupDelayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 周期体异常绝缘（CRA 8.40 W-06）：ScheduledExecutorService 周期任务一次抛出即被永久取消，
     * 清理停摆即缺陷复现——捕获记录后继续后续周期。
     */
    private void cleanupPeriodic() {
        try {
            cleanup();
        } catch (Exception e) {
            LOG.warn("nop.debugger.cleanup-fail", e);
        }
    }

    @Override
    public void close() {
        closed = true;
        cleanupThread.shutdown();
        tempBreakpoint = null;
        monitorNotify(DebugStepMode.RESUME);
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

    @Override
    public void stepInto() {
        SuspendedThread thread = this.lastSuspendThread;
        if (thread == null) {
            LOG.info("nop.debugger.step-into-no-suspended-thread");
            return;
        }
        monitorNotify(DebugStepMode.STEP_INTO);
    }

    @Override
    public void stepOver() {
        SuspendedThread thread = this.lastSuspendThread;
        if (thread == null) {
            LOG.info("nop.debugger.step-over-no-suspended-thread");
            return;
        }
        monitorNotify(DebugStepMode.STEP_OVER);
    }

    @Override
    public void stepOut() {
        SuspendedThread thread = this.lastSuspendThread;
        if (thread == null) {
            LOG.info("nop.debugger.step-out-no-suspended-thread");
            return;
        }
        monitorNotify(DebugStepMode.STEP_OUT);
    }

    @Override
    public void suspend() {
        this.stepMode = DebugStepMode.SUSPEND;
        this.suspended = true;
        monitorLock.lock();
        try {
            suspendedCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
    }

    @Override
    public void resume() {
        monitorNotify(DebugStepMode.RESUME);
    }

    private void monitorNotify(DebugStepMode stepMode) {
        this.stepMode = stepMode;
        monitorLock.lock();
        try {
            // 清标志移入锁内（W-04：原锁外清 suspended 落在 doSuspend 置位与守卫读之间即失配），
            // 与纪元推进同一临界区——resume 与挂起交接原子化
            this.suspended = false;
            resumeEpoch++;
            resumeCondition.signalAll();
        } finally {
            monitorLock.unlock();
        }
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
            EvalRuntime rt = thread.getEvalRuntime();
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
            return thread.getStackInfo();
        }
    }

    @Override
    public List<DebugVariable> getScopeVariables(long threadId) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            return Collections.emptyList();
        synchronized (thread) {
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
        if (loc == null || breakpointsMuted)
            return;

        if (!checkTempBreakpoint(loc, rt)) {
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

    private void checkStepInto(SourceLocation loc, EvalRuntime rt) {
        stepMode = DebugStepMode.RESUME;
        long suspendEpoch = resumeEpoch; // 宣告前快照（W-04：回调内同步投递的 resume 归属本停点）
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.update(loc, rt);

        if (notifier != null) {
            notifier.notifyStepInto(thread, loc, rt);
        }
        doSuspend(thread, suspendEpoch);
    }

    private boolean checkTempBreakpoint(SourceLocation loc, EvalRuntime rt) {
        if (!matchTempBreakpoint(loc, rt))
            return false;
        stepMode = DebugStepMode.RESUME;
        long suspendEpoch = resumeEpoch; // 宣告前快照（W-04）
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread, suspendEpoch);
        return true;
    }

    private void checkSuspend(SourceLocation loc, EvalRuntime rt) {
        stepMode = DebugStepMode.RESUME;
        long suspendEpoch = resumeEpoch; // 宣告前快照（W-04）
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifySuspend(thread, loc, rt);
        }
        doSuspend(thread, suspendEpoch);
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
                stepMode = DebugStepMode.RESUME;
                long suspendEpoch = resumeEpoch; // 宣告前快照（W-04）
                thread.update(loc, rt);
                if (notifier != null) {
                    notifier.notifyStepOver(thread, loc, rt);
                }
                doSuspend(thread, suspendEpoch);
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
            stepMode = DebugStepMode.RESUME;
            long suspendEpoch = resumeEpoch; // 宣告前快照（W-04）

            thread.update(loc, rt);
            if (notifier != null) {
                notifier.notifyStepOut(thread, loc, rt);
            }
            doSuspend(thread, suspendEpoch);
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

        stepMode = DebugStepMode.RESUME;
        long suspendEpoch = resumeEpoch; // 宣告前快照（W-04）
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread, suspendEpoch);
        return true;
    }

    private void doSuspend(SuspendedThread thread, long suspendEpoch) {
        monitorLock.lock();
        try {
            // 纪元已前进：快照之后已有 resume 到达（含 notifier 回调内同步投递）——本停点直接完成，
            // 不发布任何挂起标志（per-thread 与全局 suspended 均不置位），lastSuspendThread 照常发布。
            // 可观测新语义：停点快照之后到达的 resume 一定生效，不再挂死。
            if (resumeEpoch != suspendEpoch) {
                lastSuspendThread = thread;
                return;
            }
            // Mark the per-thread suspended flag BEFORE publishing the volatile global flag,
            // so that any observer that sees suspended==true is guaranteed to also see this
            // thread as suspended (the volatile write establishes a happens-before barrier).
            // Without this ordering, getSuspendedThreads() could observe suspended==true but
            // an empty thread list, since thread.setSuspended(true) previously lagged behind.
            // 既有可见性序注释语义零回退（W-04 修复不得回退此前次修复）。
            thread.setSuspended(true);
            suspended = true;
            lastSuspendThread = thread;
            suspendedCondition.signalAll();
            while (resumeEpoch == suspendEpoch && !closed) {
                if (resumeCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) {
                    LOG.trace("nop.debugger.monitor-await-timeout");
                }
            }
        } catch (InterruptedException e) {
            // ignore：与既有 monitorWait 一致，恢复中断标志后按 resume 语义退出等待
            Thread.currentThread().interrupt();
        } finally {
            thread.setSuspended(false);
            monitorLock.unlock();
        }
        if (closed) {
            throw new NopEvalException(ERR_XLANG_DEBUGGER_ALREADY_CLOSED);
        }
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