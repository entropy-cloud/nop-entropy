/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import java.util.function.Consumer;

public interface IBatchProcessListener<S, R, C> {
    default void onProcessBegin(S item, Consumer<R> consumer, C context) {
    }

    default void onProcessEnd(Throwable e, S item, Consumer<R> consumer, C context) {
    }
}