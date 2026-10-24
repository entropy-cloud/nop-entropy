/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE;
import static io.nop.cli.CliErrors.ERR_CLI_FILE_NOT_EXISTS;
import static io.nop.cli.CliErrors.ERR_CLI_FILE_NOT_TASK_FILE;

@CommandLine.Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Run script file(s)"
)
public class CliRunCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliRunCommand.class);

    @CommandLine.Parameters(index = "0", description = "Script file path or directory. If a directory, all .xrun files inside are executed")
    File file;

    @CommandLine.Option(names = {"-i", "--input"}, description = "Input parameters (JSON)")
    String input;

    @CommandLine.Option(names = {"-o", "--output"}, description = "Output directory")
    File outputDir;

    @CommandLine.Option(names = {"-t", "--interval"}, description = "Repeat interval in milliseconds")
    int interval;

    @CommandLine.Option(
        names = "-P",
        description = "Dynamic parameter (format: -Pname=value)",
        paramLabel = "KEY=VALUE"
    )
    Map<String, String> dynamicParams = new HashMap<>();

    public Integer call() {
        if (!file.exists()) {
            throw new NopException(ERR_CLI_FILE_NOT_EXISTS)
                    .status(-100)
                    .param(ARG_PATH, file.getAbsolutePath());
        }

        Map<String, Object> globalState = new ConcurrentHashMap<>();
        if (input != null) {
            Map<String, Object> map = (Map<String, Object>) JsonTool.parseNonStrict(null, input);
            if (map != null)
                globalState.putAll(map);
        }

        if (dynamicParams != null) {
            globalState.putAll(dynamicParams);
        }

        File output = outputDir;
        if (output == null)
            output = FileHelper.currentDir();

        globalState.put("outputDir", output);

        runTasks(globalState);

        if (interval > 0) {
            Future<?> watchFuture = startWatchLoop(globalState);

            try {
                System.in.read();
            } catch (IOException e) {
                LOG.info("nop.cli.run.end");
            }
        }
        return 0;
    }

    /**
     * watch 周期链（plan 2496 4.5-C4-001）：返回 future 不再丢弃——平台绝缘设施保证任务体
     * Throwable 被捕获观测、链重臂（单轮脚本失败不静默停摆 watch），本方法挂 whenComplete
     * 把失败显式落日志（失败可见化）。包私有可见性供回归测试。
     */
    Future<?> startWatchLoop(Map<String, Object> globalState) {
        Future<?> watchFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(() -> {
            runTasks(globalState);
        }, interval, interval, TimeUnit.MILLISECONDS);
        // C4-001：失败可见化。返回 future 是失败/取消/拒绝信号（首次失败即异常完结，链继续重臂）
        if (watchFuture instanceof java.util.concurrent.CompletableFuture<?>) {
            ((java.util.concurrent.CompletableFuture<?>) watchFuture).whenComplete((result, error) -> {
                if (error != null && !(error instanceof java.util.concurrent.CancellationException)) {
                    LOG.error("nop.cli.run.watch-round-failed:path={}", file.getAbsolutePath(), error);
                }
            });
        }
        return watchFuture;
    }

    protected void runTasks(Map<String, Object> globalState) {
        if (file.isFile()) {
            if (!file.getName().endsWith(".xrun"))
                throw new NopException(ERR_CLI_FILE_NOT_TASK_FILE)
                        .param(ARG_PATH, file.getAbsolutePath());
            runFile(file, globalState);
        } else if (file.isDirectory()) {
            boolean hasTaskFile = false;
            File[] subFiles = file.listFiles();
            Arrays.sort(subFiles);
            for (File subFile : subFiles) {
                if (subFile.getName().endsWith(".xrun")) {
                    runFile(subFile, globalState);
                    hasTaskFile = true;
                }
            }
            if (!hasTaskFile)
                throw new NopException(ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE)
                        .param(ARG_PATH, file.getAbsolutePath());
        }
    }

    private void runFile(File file, Map<String, Object> globalState) {
        String path = FileHelper.getFileUrl(file);
        XplModel xpl = (XplModel) ResourceComponentManager.instance().loadComponentModel(path);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValues(globalState);
        scope.setLocalValue("globalState", globalState);
        xpl.invoke(scope);
    }
}