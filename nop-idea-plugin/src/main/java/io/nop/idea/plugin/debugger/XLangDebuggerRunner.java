/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.idea.plugin.execution.XLangDebugExecutor;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.PsiClassHelper;
import io.nop.xlang.debugger.XLangDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

/**
 * 选择XLang Runner来执行java代码时自动启动XLang调试器
 */
public class XLangDebuggerRunner extends GenericDebuggerRunner {
    private static final String XLANG_DEBUG_RUNNER_ID = "XLangDebugRunner";

    @Override
    public @NotNull String getRunnerId() {
        return XLANG_DEBUG_RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(XLangDebugExecutor.EXECUTOR_ID) // 与 XLangDebugExecutor 绑定
               && ( // 同时支持 Application 和 JUnit 类型的 Run Configuration，
                    // 即，可对 main() 函数和单元测试进行 XLang 调试
                    profile instanceof JavaRunConfigurationBase
                    // 支持 Gradle 类型的 Run Configuration
                    || profile instanceof GradleRunConfiguration);
    }

    @Override
    protected RunContentDescriptor createContentDescriptor(
            @NotNull RunProfileState state, @NotNull ExecutionEnvironment environment
    ) throws ExecutionException {
        if (noXLangDebuggerInProject(environment)) {
            // Note: 确保 Messages#showErrorDialog 在事件分发线程 (EDT) 中调用，
            // 避免出现异常 "Access is allowed from Event Dispatch Thread (EDT) only"
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showErrorDialog(NopPluginBundle.message("xlang.debugger.not-exist"), "XLang Debugger");
            });
            return null;
        }

        XLangRunConfigurationExtension extension = //
                RunConfigurationExtension.EP_NAME.findExtensionOrFail(XLangRunConfigurationExtension.class);

        int xlangDebugPort = extension.getXLangDebugPort();
        DebugEnvironment debugEnvironment = extension.createDebugEnvironment(state, environment);

        return dispatch(()-> {
            // 实际上同时启动了java调试器和XLang调试器
            XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
                @NotNull
                @Override
                public XDebugProcess start(@NotNull XDebugSession debugSession) throws ExecutionException {
                    DebuggerSession debuggerSession = //
                            DebuggerManagerEx.getInstanceEx(environment.getProject()) //
                                             .attachVirtualMachine(debugEnvironment);
                    assert debuggerSession != null;

                    debuggerSession.getContextManager() //
                                   .addListener(new XLangDebugContextListener(debugSession, debuggerSession));

                    return new XLangDebugProcess(debugSession, debuggerSession, xlangDebugPort);
                }
            });

            return session.getRunContentDescriptor();
        });
    }

    /** 当前项目是否未依赖 {@link XLangDebugger} */
    private boolean noXLangDebuggerInProject(@NotNull ExecutionEnvironment environment) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            // Note: 避免出现异常 "Read access is allowed from inside read-action only"
            try {
                return PsiClassHelper.findClass(environment.getProject(), XLangDebugger.class.getName()) == null;
            } catch (Exception ignore) {
                // Note: 可能存在索引还未创建完毕的异常，这里直接忽略即可
                return false;
            }
        });
    }

    private <T> T dispatch(Callable<T> task) {
        if (EventQueue.isDispatchThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }

        CompletableFuture<T> promise = new CompletableFuture<>();
        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    T ret = task.call();
                    promise.complete(ret);
                } catch (Throwable e) {
                    promise.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
        return FutureHelper.syncGet(promise);
    }
}
