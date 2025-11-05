/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.execution;

import javax.swing.*;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebuggerBundle;
import io.nop.idea.plugin.icons.NopIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class XLangDebugExecutor extends Executor {
    @NonNls
    public static final String EXECUTOR_ID = "XLangDebug";

    @NotNull
    @Override
    public String getToolWindowId() {
        return ToolWindowId.DEBUG;
    }

    @NotNull
    @Override
    public Icon getToolWindowIcon() {
        return NopIcons.Tool_XLangDebug;
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return NopIcons.Action_XLangDebug;
    }

    @Override
    public Icon getDisabledIcon() {
        return IconLoader.getDisabledIcon(getIcon());
    }

    @Override
    @NotNull
    public String getActionName() {
        return "XLangDebug";
    }

    @Override
    @NotNull
    public String getId() {
        return EXECUTOR_ID;
    }

    @Override
    public String getContextActionId() {
        return "XLangDebugClass";
    }

    @Override
    @NotNull
    public String getStartActionText() {
        return "&XLangDebug";
    }

    @Override
    public String getDescription() {
        return XDebuggerBundle.message("string.debugger.runner.description");
    }

    @Override
    public String getHelpId() {
        return "debugging.DebugWindow";
    }

    @Override
    public boolean isSupportedOnTarget() {
        return EXECUTOR_ID.equalsIgnoreCase(getId());
    }

    public static Executor getDebugExecutorInstance() {
        return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
    }
}
