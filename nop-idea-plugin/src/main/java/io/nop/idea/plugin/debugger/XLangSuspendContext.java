/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XSuspendContext;
import io.nop.api.debugger.StackInfo;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/**
 * 管理多个ExecutionStack，每个线程对应一个ExecutionStack
 */
public class XLangSuspendContext extends XSuspendContext {
    private final XLangDebugProcess debugProcess;
    private XLangExecutionStack myActiveStack;
    private List<XLangExecutionStack> myExecutionStacks = new LinkedList<>();

    public XLangSuspendContext(@NotNull XLangDebugProcess process, @NotNull StackInfo stackInfo) {
        this.debugProcess = process;
        addExecutionStack(stackInfo);
    }

    public XLangExecutionStack addExecutionStack(StackInfo stackInfo) {
        XLangExecutionStack stack = new XLangExecutionStack(debugProcess, this, stackInfo);
        removeExecutionStack(stackInfo.getThreadId());
        myExecutionStacks.add(stack);
        myActiveStack = stack;
        return stack;
    }

    public void removeExecutionStack(long threadId) {
        for (int i = 0, n = myExecutionStacks.size(); i < n; i++) {
            if (myExecutionStacks.get(i).getThreadId() == threadId) {
                myExecutionStacks.remove(i);
                break;
            }
        }
    }

    @Override
    public XExecutionStack getActiveExecutionStack() {
        return myActiveStack;
    }

    public void setActiveExecutionStack(XLangExecutionStack stack) {
        myActiveStack = stack;
    }

    @Override
    public XExecutionStack[] getExecutionStacks() {
        return myExecutionStacks.toArray(new XExecutionStack[myExecutionStacks.size()]);
    }
}
