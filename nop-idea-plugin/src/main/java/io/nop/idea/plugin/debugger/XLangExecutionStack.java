/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.debugger;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import io.nop.api.debugger.StackInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 对应于处于挂起状态的单个线程
 */
public class XLangExecutionStack extends XExecutionStack {

    private final long threadId;
    @NotNull
    private final XLangDebugProcess myProcess;
    @NotNull
    private final List<XLangStackFrame> frames;

    private final XLangSuspendContext myContext;

    public XLangExecutionStack(@NotNull XLangDebugProcess process, XLangSuspendContext context,
                               StackInfo stackInfo) {
        super("Thread #" + stackInfo.getThreadId());
        this.threadId = stackInfo.getThreadId();
        this.myContext = context;
        this.myProcess = process;
        this.frames = new ArrayList<>(stackInfo.getStackTrace().size());
        for (int i = 0, n = stackInfo.getStackTrace().size(); i < n; i++) {
            frames.add(new XLangStackFrame(myProcess, stackInfo.getThreadId(),
                    stackInfo.getStackTrace().get(i), i));
        }
    }

    @Nullable
    @Override
    public XStackFrame getTopFrame() {
        return ContainerUtil.getFirstItem(frames);
    }

    @Override
    public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
        if (firstFrameIndex <= frames.size()) {
            container.addStackFrames(frames.subList(firstFrameIndex, frames.size()), true);
        } else {
            container.addStackFrames(Collections.emptyList(), true);
        }

        myContext.setActiveExecutionStack(this);
    }

    public long getThreadId() {
        return threadId;
    }
}