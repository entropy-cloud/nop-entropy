/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.eval;

import io.nop.api.core.annotations.core.NoReflection;
import io.nop.api.core.util.SourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EvalFrame {
    // 函数参数 + 函数内变量 + 闭包变量构成的集合
    private final Object[] stack;

    // 函数调用堆栈
    private final EvalFrame parentFrame;

    private SourceLocation location;
    private String displayInfo;

    // 对应stack的每一个元素的变量名
    private final String[] varNames;

    // stack的每一个元素的最后一次赋值的位置
    private SourceLocation[] varAssignLocations;

    public EvalFrame(EvalFrame parentFrame, String[] slotNames, Object[] stack) {
        this.parentFrame = parentFrame;
        this.varNames = slotNames;
        this.stack = stack;
    }

    public EvalFrame(EvalFrame parentFrame, String[] slotNames) {
        this(parentFrame, slotNames, new Object[slotNames.length]);
    }

    public EvalFrame getParentFrame(int parentLevel) {
        EvalFrame frame = this;
        for (int i = 0; i < parentLevel; i++) {
            frame = frame.getParentFrame();
            if (frame == null)
                throw new IllegalStateException("nop.err.core.invalid-frame-parent-level:" + parentLevel);
        }
        return frame;
    }

    public EvalFrame getParentFrame() {
        return parentFrame;
    }

    public Object getStackValue(int slot) {
        return stack[slot];
    }

    @NoReflection
    public void setStackValue(int slot, Object value) {
        this.stack[slot] = value;
    }

    public Object getClosureValue(int slot) {
        return stack[slot];
    }

    public Object getArg(int slot) {
        return stack[slot];
    }

    @NoReflection
    public void setArg(int slot, Object value) {
        stack[slot] = value;
    }

    public Object getVar(int slot) {
        return stack[slot];
    }

    @NoReflection
    public void setVar(int slot, Object value) {
        stack[slot] = value;
    }

    public EvalReference getRef(int slot) {
        return (EvalReference) getStackValue(slot);
    }

    @NoReflection
    public void setRefValue(int slot, Object value) {
        EvalReference ref = (EvalReference) getStackValue(slot);
        if (ref == null) {
            setStackValue(slot, new EvalReference(value));
        } else {
            ref.setValue(value);
        }
    }

    public String getVarName(int slot) {
        return varNames[slot];
    }

    public SourceLocation getVarAssignLocation(int slot) {
        if (varAssignLocations == null)
            return null;
        return varAssignLocations[slot];
    }

    @NoReflection
    public void setVarAssignLocations(int slot, SourceLocation loc) {
        if (varAssignLocations == null)
            varAssignLocations = new SourceLocation[varNames.length];
        varAssignLocations[slot] = loc;
    }

    @NoReflection
    public void setDisplayInfo(SourceLocation loc, String displayInfo) {
        this.location = loc;
        this.displayInfo = displayInfo;
    }

    public SourceLocation getLocation() {
        return location;
    }

    public String getDisplayInfo() {
        return displayInfo;
    }

    public Map<String, Object> getVariableMap() {
        Map<String, Object> ret = new LinkedHashMap<>();

        for (int i = 0, n = stack.length; i < n; i++) {
            String name = varNames == null ? null : varNames[i];
            Object value = EvalReference.deRef(stack[i]);
            ret.put(name, value);
        }
        return ret;
    }

    public int getStackSize() {
        return stack.length;
    }

    public List<FrameVariable> getFrameVariables() {
        List<FrameVariable> ret = new ArrayList<>(stack.length);
        for (int i = 0, n = stack.length; i < n; i++) {
            String name = varNames == null ? null : varNames[i];
            SourceLocation loc = varAssignLocations == null ? null : varAssignLocations[i];
            Object value = EvalReference.deRef(stack[i]);
            FrameVariable var = new FrameVariable(i, name, loc, value);
            ret.add(var);
        }
        return ret;
    }
}