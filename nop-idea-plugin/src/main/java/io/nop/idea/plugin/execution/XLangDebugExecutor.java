/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


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
        return AllIcons.RunConfigurations.RemoteDebug;
    }

    @Override
    @NotNull
    public Icon getIcon() {
        return AllIcons.Actions.StartDebugger;
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
