/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.command;

import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 执行命令行指令。返回值为exe的exitCode，0表示成功，-1表示失败
 */
public interface ICommandExecutor {

    int execute(String command, Map<String, Object> params);

    default CompletionStage<Integer> executeAsync(String command, Map<String, Object> params) {
        return FutureHelper.futureCall(() -> execute(command, params));
    }
}