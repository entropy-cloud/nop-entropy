/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletableFuture;

/**
 * 通过{@link BatchTaskBuilder}来创建IBatchTask对象。
 * <p>
 * 批量处理由{@link IBatchLoader}, {@link IBatchProcessor}和{@link IBatchConsumer}三个核心接口构成。
 * 基本处理逻辑为：在一个批次中批量加载一批数据，逐个调用processor进行处理，收集到所有数据之后再批量调用consumer处理
 */
public interface IBatchTask {
    CompletableFuture<Void> executeAsync(IBatchTaskContext context);

    default void execute(IBatchTaskContext context) {
        FutureHelper.syncGet(executeAsync(context));
    }
}