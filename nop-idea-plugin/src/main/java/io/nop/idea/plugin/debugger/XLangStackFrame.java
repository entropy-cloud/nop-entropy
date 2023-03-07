/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import io.nop.api.debugger.DebugVariable;
import io.nop.api.debugger.IDebuggerAsync;
import io.nop.api.debugger.StackTraceElement;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XLangStackFrame extends XStackFrame {

    private final XLangDebugProcess debugProcess;
    private final StackTraceElement stackFrame;
    private final long threadId;
    private final int frameIndex;

    private XSourcePosition pos;

    XLangStackFrame(@NotNull XLangDebugProcess process, long threadId, @NotNull StackTraceElement frame, int frameIndex) {
        debugProcess = process;
        stackFrame = frame;
        this.threadId = threadId;
        this.frameIndex = frameIndex;
    }

    public XLangDebugProcess getDebugProcess() {
        return debugProcess;
    }

    public long getThreadId() {
        return threadId;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        XSourcePosition position = getSourcePosition();
        if (position != null) {
            component.append(stackFrame.getFuncName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            String fileName = StringHelper.fileFullName(stackFrame.getSourcePath());
            component.append(":" + stackFrame.getLine() + "," + fileName, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
            component.append(" (" + StringHelper.filePath(stackFrame.getSourcePath()) + ")",
                    SimpleTextAttributes.GRAY_ATTRIBUTES);
            component.setIcon(AllIcons.Debugger.Frame);
        } else {
            component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
    }

    @Override
    public void computeChildren(@NotNull final XCompositeNode node) {
        IDebuggerAsync debugger = debugProcess.getDebugger();
        if (debugger == null) {
            super.computeChildren(node);
        } else {
            debugger.getFrameVariablesAsync(threadId, frameIndex)
                    .whenComplete((vars, err) -> {
                        if (err != null) {
                            super.computeChildren(node);
                        } else {
                            XValueChildrenList list = new XValueChildrenList(vars.size());

                            for (DebugVariable var : vars) {
                                list.add(var.getName(), new XLangValue(this, var));
                            }
                            node.addChildren(list, true);

                            debugProcess.getSession().reportError(err.getMessage());
                        }
                    });
        }
    }

    @Nullable
    @Override
    public XSourcePosition getSourcePosition() {
        if (pos == null) {
            pos = ProjectFileHelper.buildPos(stackFrame.getSourcePath(), stackFrame.getLine());
        }
        return pos;
    }

    /**
     * 计算表达式的值
     *
     * @return
     */
    @Nullable
    @Override
    public XDebuggerEvaluator getEvaluator() {

        return new XDebuggerEvaluator() {
            @Override
            public void evaluate(@NotNull String text, @NotNull XEvaluationCallback evaluationCallback,
                                 @Nullable XSourcePosition sourceLocation) {
                IDebuggerAsync debugger = debugProcess.getDebugger();
                if (debugger != null) {
                    debugger.getExprValueAsync(threadId, frameIndex, text)
                            .whenComplete((ret, err) -> {
                                if (err != null) {
                                    DebugVariable var = new DebugVariable();
                                    var.setKind("exception");
                                    var.setValue(err.toString());
                                    evaluationCallback.evaluated(new XLangValue(XLangStackFrame.this, var));
                                } else {
                                    evaluationCallback.evaluated(new XLangValue(XLangStackFrame.this, ret));
                                }
                            });
                } else {
                    DebugVariable var = new DebugVariable();
                    var.setKind("invalid");
                    evaluationCallback.evaluated(new XLangValue(XLangStackFrame.this, var));
                }
                // debugProcess.evalExpr(text, 0, evaluationCallback);
            }


        };
    }
}

