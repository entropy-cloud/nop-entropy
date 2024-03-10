/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.List;

public interface IBatchLoadListener<S, C> {
    default void onLoadBegin(int batchSize, C context) {
    }

    default void onLoadEnd(Throwable ex, List<S> items, int batchSize, C context) {
    }
}