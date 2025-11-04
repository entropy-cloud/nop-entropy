/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.command;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.core.command.args.CommandLineArgs;

/**
 * 用于保存全局的命令行参数
 */
@GlobalInstance
public class ApplicationArguments {
    static CommandLineArgs s_arguments;

    public static CommandLineArgs get() {
        return s_arguments;
    }

    public static void set(CommandLineArgs arguments) {
        s_arguments = arguments;
    }
}
