/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.shell;

import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.config.IConfigReference;

import static io.nop.api.core.config.AppConfig.varRef;

public interface ShellConfigs {
    @Description("Shell内部脚本文件存放在指定目录下，每个文件对应一个脚本任务，脚本文件名除去文件扩展名后为任务名")
    IConfigReference<String> CFG_SHELL_TASK_ROOT_DIR = varRef("nop.shell.task.root-dir", String.class,
            "/nop/shell-tasks/");

}
