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
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import io.nop.api.core.exceptions.NopException;
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
    ) {
        XLangRunConfigurationExtension extension = //
                RunConfigurationExtension.EP_NAME.findExtensionOrFail(XLangRunConfigurationExtension.class);

        int xlangDebugPort = extension.getXLangDebugPort();
        DebugEnvironment debugEnvironment = extension.createDebugEnvironment(state, environment);

        if (notIncludeXLangDebugger(environment.getProject(), debugEnvironment)) {
            // Note: 确保 Messages#showErrorDialog 在事件分发线程 (EDT) 中调用，
            // 避免出现异常 "Access is allowed from Event Dispatch Thread (EDT) only"
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showErrorDialog(NopPluginBundle.message("xlang.debugger.not-exist"), "XLang Debugger");
            });
            return null;
        }

        return dispatch(()-> {
            // 实际上同时启动了java调试器和XLang调试器
            DebuggerSession debuggerSession = //
                    DebuggerManagerEx.getInstanceEx(environment.getProject()) //
                                     .attachVirtualMachine(debugEnvironment);
            assert debuggerSession != null;

            XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
                @NotNull
                @Override
                public XDebugProcess start(@NotNull XDebugSession debugSession) {
                    debuggerSession.getContextManager() //
                                   .addListener(new XLangDebugContextListener(debugSession, debuggerSession));

                    return new XLangDebugProcess(debugSession, debuggerSession, xlangDebugPort);
                }
            });

            return session.getRunContentDescriptor();
        });
    }

    /** 当前调试环境中是否未引入 {@link XLangDebugger} */
    private boolean notIncludeXLangDebugger(@NotNull Project project, @NotNull DebugEnvironment environment) {
        return ApplicationManager.getApplication().runReadAction((Computable<Boolean>) () -> {
            // Note: 避免出现异常 "Read access is allowed from inside read-action only"
            GlobalSearchScope scope = environment.getSearchScope();
            try {
                return PsiClassHelper.findClass(project, XLangDebugger.class.getName(), scope) == null;
            } catch (Exception ignore) {
                // Note: 可能存在索引还未创建完毕的异常，这里直接忽略即可
                return false;
            }
        });
    }

    private <T> T dispatch(Callable<T> task) {
        // 参考 com.intellij.debugger.impl.GenericDebuggerRunner#attachVirtualMachine
        AtomicReference<Exception> ex = new AtomicReference<>();
        AtomicReference<T> result = new AtomicReference<>();

        // Note: 通过 Application#invokeAndWait 处理 AWT Event Dispatch Thread (EDT) 并获得执行结果，
        // 避免出现异常 "Running sync tasks on pure EDT (w/o IW lock) is dangerous for several reasons"
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                result.set(task.call());
            } catch (ProcessCanceledException ignored) {
            } catch (Exception e) {
                ex.set(e);
            }
        });

        if (ex.get() != null) {
            throw NopException.adapt(ex.get());
        }
        return result.get();
    }
}
