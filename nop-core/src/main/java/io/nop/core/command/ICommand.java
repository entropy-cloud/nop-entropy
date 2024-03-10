/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.command;

import io.nop.api.core.util.FutureHelper;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface ICommand {

    int execute(Map<String, Object> params);

    default CompletionStage<Integer> executeAsync(Map<String, Object> params) {
        return FutureHelper.futureCall(() -> execute(params));
    }
}