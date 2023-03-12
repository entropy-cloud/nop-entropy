/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cli.commands;

import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "watch",
        mixinStandardHelpOptions = true,
        description = "监控指定目录或者文件的变化"
)
public class CliWatchCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..*", description = "监控文件目录")
    String[] files;

    @CommandLine.Option(names = {"-e", "--execute"},
            description = "发现文件变动后执行代码")
    String execute;

    @Override
    public Integer call() throws Exception {
        return null;
    }
}