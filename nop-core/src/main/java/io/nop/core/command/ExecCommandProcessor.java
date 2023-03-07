/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.command;

import io.nop.api.core.ioc.BeanContainer;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.command.args.CommandLineArgs;
import io.nop.core.command.args.SimpleCommandLineArgsParser;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.impl.FileResource;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 识别exec命令,将--command参数解析为CommandBean，并调用appCommandExecutor来执行命令
 */
public class ExecCommandProcessor {
    private final boolean exitAfterExec;

    public ExecCommandProcessor(boolean exitAfterExec) {
        this.exitAfterExec = exitAfterExec;
    }

    public ExecCommandProcessor() {
        this(true);
    }

    public boolean process(String[] args) {
        SimpleCommandLineArgsParser parser = new SimpleCommandLineArgsParser();
        return process(parser.parse(args));
    }

    public boolean process(CommandLineArgs cmdArgs) {
        if (!cmdArgs.isSubCommand("exec")) {
            return false;
        }

        List<String> commands = cmdArgs.getOptionValues("command");
        for (String command : commands) {
            if (command == null || StringHelper.isEmpty(command))
                continue;

            CommandBean commandBean;
            if (command.endsWith(".json") || command.endsWith(".json5") || command.endsWith(".yaml")) {
                // run command json file
                FileResource resource = new FileResource(new File(command));
                Object o = JsonTool.parseBeanFromResource(resource, CommandBean.class);
                commandBean = (CommandBean) o;
            } else {
                Object o = JsonTool.parseBeanFromText(command, CommandBean.class);
                commandBean = (CommandBean) o;
            }
            execCommand(commandBean);
        }

        if (exitAfterExec) {
            System.exit(0);
        }

        return true;
    }

    private void execCommand(CommandBean commandBean) {
        Guard.notEmpty(commandBean.getCommand(), "command");
        Map<String, Object> params = commandBean.getParams();
        if (params == null)
            params = new HashMap<>();

        int exitCode;
        String commandBeanName = commandBean.getCommand() + "Command";
        if (BeanContainer.instance().containsBean(commandBeanName)) {
            ICommand cmd = (ICommand) BeanContainer.instance().getBean(commandBeanName);
            exitCode = cmd.execute(params);
        } else {
            ICommandExecutor executor = (ICommandExecutor) BeanContainer.instance().getBean("appCommandExecutor");
            exitCode = executor.execute(commandBean.getCommand(), params);
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}