/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.debugger;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DefaultDebugEnvironment;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.JavaRunConfigurationBase;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState;
import com.intellij.util.net.NetUtils;
import io.nop.api.core.exceptions.NopException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_PORT;

/**
 * 通过单独的 {@link RunConfigurationExtension} 向
 * {@link ExternalSystemRunnableState#getJvmParametersSetup()}
 * 注入 jvm 参数，以支持对 Gradle 项目的调试
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-27
 */
public class XLangRunConfigurationExtension extends RunConfigurationExtension {
    private static final String LOCALHOST = "127.0.0.1";

    private static final String XDEBUG = "-Xdebug";
    private static final String JDWP = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=";

    private int[] ports;

    public DebugEnvironment createDebugEnvironment(
            @NotNull RunProfileState state, @NotNull ExecutionEnvironment environment
    ) {
        int javaDebugPort = getJavaDebugPort();
        RemoteConnection connection = new RemoteConnection(true, LOCALHOST, String.valueOf(javaDebugPort), false);

        // Note: gradle 的启动时间较长，因此，需设置更长的等待超时时间
        return new DefaultDebugEnvironment(environment, state, connection, DebugEnvironment.LOCAL_START_TIMEOUT);
    }

    @Override
    public boolean isApplicableFor(@NotNull RunConfigurationBase<?> configuration) {
        return configuration instanceof JavaRunConfigurationBase //
               || configuration instanceof GradleRunConfiguration;
    }

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(
            @NotNull T configuration, @NotNull JavaParameters params, RunnerSettings runnerSettings,
            @NotNull Executor executor
    ) throws ExecutionException {
        updateJavaParameters(configuration, params, runnerSettings);

        JavaProgramPatcher.runCustomPatchers(params, executor, configuration);
    }

    @Override
    public <T extends RunConfigurationBase<?>> void updateJavaParameters(
            @NotNull T configuration, @NotNull JavaParameters params, @Nullable RunnerSettings runnerSettings
    ) throws ExecutionException {
        int javaDebugPort = getJavaDebugPort();
        int xlangDebugPort = getXLangDebugPort();
        ParametersList paramsList = params.getVMParametersList();

        // Making the assumption that it's JVM 7 onwards
        paramsList.addParametersString(XDEBUG);

        // Debugger port
        String remotePort = JDWP + LOCALHOST + ':' + javaDebugPort;
        paramsList.addParametersString(remotePort);

        // 传入调试开关和调试端口
        paramsList.addParametersString("-D" + CFG_XLANG_DEBUGGER_ENABLED.getName() + "=true");
        paramsList.addParametersString("-D" + CFG_XLANG_DEBUGGER_PORT.getName() + '=' + xlangDebugPort);
    }

    public int getJavaDebugPort() {
        return getPorts()[0];
    }

    public int getXLangDebugPort() {
        return getPorts()[1];
    }

    private int[] getPorts() {
        if (ports == null) {
            try {
                ports = NetUtils.findAvailableSocketPorts(2);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }
        return ports;
    }
}
