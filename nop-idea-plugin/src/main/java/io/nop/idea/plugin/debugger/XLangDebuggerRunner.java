/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.debugger;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.idea.plugin.execution.XLangDebugExecutor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * 选择XLang Runner来执行java代码时自动启动XLang调试器
 */
public class XLangDebuggerRunner extends GenericDebuggerRunner {
    private static final String XDEBUG = "-Xdebug";
    private static final String JDWP = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=";
    private static final String XLANG_DEBUG_ENABLED = "-Dnop.xlang.debugger.enabled=true";
    private static final String XLANG_DEBUG_PORT = "-Dnop.xlang.debugger.port=";

    private static final String LOCALHOST = "127.0.0.1";


    private static final String XLANG_DEBUG_RUNNER_ID = "XLangDebugRunner";

    public XLangDebuggerRunner() {
        super();
    }

    @Override
    public @NotNull String getRunnerId() {
        return XLANG_DEBUG_RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return executorId.equals(XLangDebugExecutor.EXECUTOR_ID) // 与 XLangDebugExecutor 绑定
               // 同时支持 Application 和 JUnit 类型的 Run Configuration，
               // 即，可对 main() 函数和单元测试进行 XLang 调试
               && profile instanceof JavaRunConfigurationBase;
    }

    @Override
    protected RunContentDescriptor createContentDescriptor(RunProfileState state, ExecutionEnvironment environment) throws ExecutionException {
        //  FileDocumentManager.getInstance().saveAllDocuments();
        // String debuggerPort = DebuggerUtils.getInstance().findAvailableDebugAddress(true);
        int[] ports;
        try {
            ports = NetUtils.findAvailableSocketPorts(2);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }

        JavaCommandLineState javaCommandLineState = (JavaCommandLineState) state;

        JavaParameters javaParameters = javaCommandLineState.getJavaParameters();
        // Making the assumption that it's JVM 7 onwards
        javaParameters.getVMParametersList().addParametersString(XDEBUG);
        // Debugger port

        String remotePort = JDWP + ports[0];
        javaParameters.getVMParametersList().addParametersString(remotePort);

        // 传入调试开关和调试端口
        javaParameters.getVMParametersList().addParametersString(XLANG_DEBUG_ENABLED);
        javaParameters.getVMParametersList().addParametersString(XLANG_DEBUG_PORT + ports[1]);

        JavaProgramPatcher.runCustomPatchers(javaParameters, environment.getExecutor(), environment.getRunProfile());

        //final ProcessHandler handler = state.execute(environment.getExecutor(),this).getProcessHandler();
        RemoteConnection connection = new RemoteConnection(true, LOCALHOST, String.valueOf(ports[0]), false);
        DebugEnvironment debugEnvironment = new DefaultDebugEnvironment(environment, state, connection, 3000L);


        return dispatch(() -> {
            final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(environment.getProject()).attachVirtualMachine(debugEnvironment);

            // 实际上同时启动了java调试器和XLang调试器
            final XDebugSession session = XDebuggerManager.getInstance(environment.getProject()).startSession(environment, new XDebugProcessStarter() {
                @NotNull
                @Override
                public XDebugProcess start(@NotNull XDebugSession xDebugSession) throws ExecutionException {
                    debuggerSession.getContextManager().addListener(new XLangDebugContextListener(xDebugSession, debuggerSession));
                    XLangDebugProcess process = new XLangDebugProcess(xDebugSession, javaCommandLineState, debuggerSession,
                            ports[1]);
                    return process;
                }
            });

            return session.getRunContentDescriptor();
        });

    }

    <T> T dispatch(Callable<T> task) {
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
