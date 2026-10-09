/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cli.exception;

import io.nop.api.core.beans.ErrorBean;
import io.nop.core.exceptions.ErrorMessageManager;
import picocli.CommandLine;

public class NopExitCodeExceptionMapper implements CommandLine.IExitCodeExceptionMapper {

    /**
     * picocli 用法错误（未知选项、缺失必填选项、参数个数错误）的退出码。
     *
     * <p>picocli 的 {@code IExitCodeExceptionMapper} 对 {@link CommandLine.ParameterException}
     * 也走本方法，而 {@code ErrorBean} 的默认 status 不是有意义的进程退出码——修复前实测
     * {@code nop-cli gen <file>}（缺必填 {@code --template}）返回 0，即用法错误被当成成功。
     * 这里显式改用 picocli 约定的 {@link CommandLine.ExitCode#USAGE}，与各工具独立 CLI
     * 对非法输入的既有口径（2）一致。
     */
    private static final int EXIT_USAGE = CommandLine.ExitCode.USAGE;

    @Override
    public int getExitCode(Throwable exception) {
        if (exception instanceof CommandLine.ExecutionException)
            exception = exception.getCause();

        if (exception instanceof CommandLine.ParameterException)
            return EXIT_USAGE;

        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exception);
        return errorBean.getStatus();
    }
}
