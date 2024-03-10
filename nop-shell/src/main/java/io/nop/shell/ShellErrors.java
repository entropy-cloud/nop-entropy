/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.shell;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface ShellErrors {
    String ARG_TASK_NAME = "taskName";
    String ARG_FILE = "file";
    String ARG_PERMS = "perms";
    String ARG_COMMAND = "command";
    String ARG_TIMEOUT = "timemout";

    ErrorCode ERR_SHELL_INVALID_TASK_NAME = define("nop.err.shell.invalid-task-name",
            "Shell任务的名称不能包含特殊字符，必须能对应到文件系统中的脚本文件:{taskName}", ARG_TASK_NAME);

    ErrorCode ERR_SHELL_NOT_ALLOW_CHANGE_FILE_PERM = define("nop.err.shell.not-allow-change-file-perm",
            "不允许修改文件权限:{file}");
    ErrorCode ERR_SHELL_EXEC_COMMAND_FAIL = define("nop.err.shell.exec-command-fail", "执行命令行程序失败:{command}",
            ARG_COMMAND);

    ErrorCode ERR_SHELL_EXEC_COMMAND_TIMEOUT = define("nop.err.shell.exec-command-timeout",
            "执行命令行程序超时:command={command},超时时间为{timeout}", ARG_COMMAND, ARG_TIMEOUT);

}
