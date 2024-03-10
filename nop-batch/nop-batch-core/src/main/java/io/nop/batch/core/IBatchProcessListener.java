/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

import java.util.function.Consumer;

public interface IBatchProcessListener<S, R, C> {
    default void onProcessBegin(S item, Consumer<R> consumer, C context) {
    }

    default void onProcessEnd(Throwable e, S item, Consumer<R> consumer, C context) {
    }
}