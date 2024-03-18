/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger;

import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.LineLocation;
import io.nop.api.debugger.StackInfo;
import io.nop.api.debugger.StackTraceElement;
import io.nop.api.debugger.ThreadInfo;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.EvalFrame;
import io.nop.core.lang.eval.EvalReference;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

public class SuspendedThread {
    private final long threadId;
    private final Thread thread;
    private final Function<SourceLocation, String> sourcePathGetter;

    private EvalRuntime rt;

    private SourceLocation lastBreakLocation;
    private int lastBreakFrameIndex;
    private boolean suspended;

    public SuspendedThread(Thread thread, EvalRuntime rt, Function<SourceLocation, String> sourcePathGetter) {
        this.threadId = thread.getId();
        this.thread = thread;
        this.rt = rt;
        this.sourcePathGetter = sourcePathGetter;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public boolean isAlive() {
        return thread.isAlive();
    }

    public SourceLocation getLastBreakLocation() {
        return lastBreakLocation;
    }

    public synchronized void update(SourceLocation lastBreakLocation, EvalRuntime rt) {
        this.lastBreakLocation = lastBreakLocation;
        this.rt = rt;
        this.lastBreakFrameIndex = getMaxFrameIndex(rt);
    }

    public int getLastBreakFrameIndex() {
        return lastBreakFrameIndex;
    }

    public EvalRuntime getEvalRuntime() {
        return rt;
    }

    public ThreadInfo getThreadInfo() {
        return new ThreadInfo(thread.getName(), threadId, suspended);
    }

    public synchronized StackInfo getStackInfo() {
        StackInfo info = new StackInfo();
        info.setThreadId(threadId);
        info.setThreadName(thread.getName());
        info.setStackTrace(getStackTrace());
        return info;
    }

    public List<DebugVariable> getScopeVariables(EvalRuntime rt) {
        return new ArrayList<>(_getScopeVariables(rt).values());
    }

    public List<DebugVariable> getFrameVariables(EvalRuntime rt, int frameIndex) {
        EvalFrame frame = rt.getFrame(frameIndex);
        if (frame == null)
            return null;
        return getDebugVariables(frame);
    }

    public int getCurrentMaxFrameIndex() {
        return getMaxFrameIndex(rt);
    }

    private int getMaxFrameIndex(EvalRuntime rt) {
        int index = -1;
        EvalFrame frame = rt.getCurrentFrame();
        while (frame != null) {
            index++;
            frame = frame.getParentFrame();
        }
        return index;
    }

    List<StackTraceElement> getStackTrace() {
        List<StackTraceElement> ret = new ArrayList<>();
        EvalFrame frame = rt.getCurrentFrame();
        SourceLocation loc = this.lastBreakLocation;
        if (loc == null) {
            loc = SourceLocation.fromPath("<invalid>");
        }

        do {
            StackTraceElement element = buildStackTraceElement(frame, loc);
            ret.add(element);
            if (frame == null)
                break;
            loc = frame.getLocation();
            frame = frame.getParentFrame();
        } while (frame != null);
        return ret;
    }

    StackTraceElement buildStackTraceElement(EvalFrame frame, SourceLocation loc) {
        String funcName = frame == null ? "__main__" : frame.getDisplayInfo();
        String cellPath = loc == null ? null : sourcePathGetter.apply(loc);
        return new StackTraceElement(cellPath, loc == null ? -1 : loc.getLine(), funcName);
    }

    Map<String, DebugVariable> _getScopeVariables(EvalRuntime rt) {
        Map<String, DebugVariable> vars = new TreeMap<>();
        IEvalScope scope = rt.getScope();
        do {
            for (String name : scope.keySet()) {
                if (vars.containsKey(name))
                    continue;

                ValueWithLocation varLoc = scope.recordValueLocation(name);
                Object value = varLoc.getValue();
                SourceLocation loc = varLoc.getLocation();

                DebugVariable var = new DebugVariable();
                var.setName(name);
                if (loc != null) {
                    LineLocation line = LineLocation.fromSourcePosition(loc, sourcePathGetter);
                    var.setAssignLoc(line);
                }
                var.setValue(valueToString(value));
                var.setType(valueType(value));
                var.setKind("scope");
                vars.put(name, var);
            }
            scope = scope.getParentScope();
        } while (scope != null);
        return vars;
    }

    List<DebugVariable> getDebugVariables(EvalFrame frame) {
        int stackSize = frame.getStackSize();
        List<DebugVariable> ret = new ArrayList<>(stackSize);

        for (int i = 0; i < stackSize; i++) {
            String name = frame.getVarName(i);
            Object value = EvalReference.deRef(frame.getVar(i));
            SourceLocation loc = frame.getVarAssignLocation(i);

            LineLocation line = LineLocation.fromSourcePosition(loc, sourcePathGetter);
            DebugVariable var = DebugValueHelper.buildDebugVariable(name, value, line);
            ret.add(var);
        }
        return ret;
    }

    String valueType(Object value) {
        return value == null ? null : value.getClass().getTypeName();
    }

    String valueToString(Object value) {
        return StringHelper.safeToString(value);
    }
}