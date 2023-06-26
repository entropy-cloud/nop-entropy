/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import io.nop.api.core.util.FutureHelper;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * <p>
 * 批量装载一批数据
 *
 * @param <S>
 */
public interface IBatchLoader<S, C> {
    /**
     * 加载数据
     *
     * @param batchSize 最多装载多少条数据
     * @return 返回空集合表示所有数据已经加载完毕
     */
    List<S> load(int batchSize, C context);

    default CompletionStage<List<S>> loadAsync(int batchSize, C context) {
        return FutureHelper.futureCall(() -> load(batchSize, context));
    }
}