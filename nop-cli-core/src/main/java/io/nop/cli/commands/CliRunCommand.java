/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.FileHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static io.nop.cli.CliErrors.ARG_PATH;
import static io.nop.cli.CliErrors.ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE;
import static io.nop.cli.CliErrors.ERR_CLI_FILE_NOT_EXISTS;
import static io.nop.cli.CliErrors.ERR_CLI_FILE_NOT_TASK_FILE;

@CommandLine.Command(
        name = "run",
        mixinStandardHelpOptions = true,
        description = "运行脚本"
)
public class CliRunCommand implements Callable<Integer> {
    static final Logger LOG = LoggerFactory.getLogger(CliRunCommand.class);

    @CommandLine.Parameters(index = "0", description = "脚本文件路径或者脚本文件目录。如果是目录，则会运行目录下所有的xrun文件")
    File file;

    @CommandLine.Option(names = {"-i", "--interval"}, description = "循环运行的时间间隔")
    int interval;


    public Integer call() {
        if (!file.exists()) {
            throw new NopException(ERR_CLI_FILE_NOT_EXISTS)
                    .status(-100)
                    .param(ARG_PATH, file.getAbsolutePath());
        }

        Map<String, Object> globalState = new ConcurrentHashMap<>();

        runTasks(globalState);

        if (interval > 0) {
            GlobalExecutors.globalTimer().scheduleWithFixedDelay(() -> {
                runTasks(globalState);
            }, interval, interval, TimeUnit.MILLISECONDS);

            try {
                System.in.read();
            } catch (IOException e) {
                LOG.info("nop.cli.run.finished");
            }
        }
        return 0;
    }

    private void runTasks(Map<String, Object> globalState) {
        if (file.isFile()) {
            if (!file.getName().endsWith(".xrun"))
                throw new NopException(ERR_CLI_FILE_NOT_TASK_FILE)
                        .param(ARG_PATH, file.getAbsolutePath());
            runFile(file, globalState);
        } else if (file.isDirectory()) {
            boolean hasTaskFile = false;
            for (File subFile : file.listFiles()) {
                if (subFile.getName().endsWith(".xrun")) {
                    runFile(subFile, globalState);
                    hasTaskFile = true;
                }
            }
            if (!hasTaskFile)
                throw new NopException(ERR_CLI_DIR_NOT_CONTAINS_TASK_FILE)
                        .status(-101)
                        .param(ARG_PATH, file.getAbsolutePath());
        }
    }

    private void runFile(File file, Map<String, Object> globalState) {
        String path = FileHelper.getFileUrl(file);
        XplModel xpl = (XplModel) ResourceComponentManager.instance().loadComponentModel(path);
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("globalState", globalState);
        xpl.invoke(scope);
    }
}