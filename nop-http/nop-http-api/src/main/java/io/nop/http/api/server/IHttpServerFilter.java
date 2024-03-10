/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.server;

import io.nop.api.core.util.IOrdered;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface IHttpServerFilter extends IOrdered {
    CompletionStage<Void> filterAsync(IHttpServerContext context, Supplier<CompletionStage<Void>> next);
}