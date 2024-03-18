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

    public XLangDebugger() {
        cleanupThread.schedule(this::cleanup, 30, TimeUnit.SECONDS);
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
                try {
                    if (!suspendedCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) {
                        LOG.trace("nop.debugger.suspend-await-timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
        this.suspended = false;
        monitorLock.lock();
        try {
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
            return null;
        synchronized (thread) {
            return thread.getScopeVariables(thread.getEvalRuntime());
        }
    }

    @Override
    public List<DebugVariable> getFrameVariables(long threadId, int frameIndex) {
        SuspendedThread thread = getSuspendedThread(threadId);
        if (thread == null)
            return null;
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
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.update(loc, rt);

        if (notifier != null) {
            notifier.notifyStepInto(thread, loc, rt);
        }
        doSuspend(thread);
    }

    private boolean checkTempBreakpoint(SourceLocation loc, EvalRuntime rt) {
        if (!matchTempBreakpoint(loc, rt))
            return false;
        stepMode = DebugStepMode.RESUME;
        SuspendedThread thread = makeSuspendedThread(rt);
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread);
        return true;
    }

    private void checkSuspend(SourceLocation loc, EvalRuntime rt) {
        stepMode = DebugStepMode.RESUME;
        SuspendedThread thread = makeSuspendedThread(rt);
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
                stepMode = DebugStepMode.RESUME;
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
            stepMode = DebugStepMode.RESUME;

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

        stepMode = DebugStepMode.RESUME;
        thread.update(loc, rt);
        if (notifier != null) {
            notifier.notifyBreakAt(thread, loc, rt);
        }
        doSuspend(thread);
        return true;
    }

    private void doSuspend(SuspendedThread thread) {
        suspended = true;
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

    private void monitorWait(SuspendedThread thread) {
        if (suspended) {
            monitorLock.lock();
            try {
                thread.setSuspended(true);
                suspendedCondition.signalAll();
                while (suspended && !closed) {
                    if (resumeCondition.await(monitorWaitInterval, TimeUnit.MILLISECONDS)) {
                        LOG.trace("nop.debugger.monitor-await-timeout");
                    }
                }
            } catch (InterruptedException e) {
                // ignore
                Thread.currentThread().interrupt();
            } finally {
                thread.setSuspended(false);
                monitorLock.unlock();
            }
        }
        if (closed) {
            throw new NopEvalException(ERR_XLANG_DEBUGGER_ALREADY_CLOSED);
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