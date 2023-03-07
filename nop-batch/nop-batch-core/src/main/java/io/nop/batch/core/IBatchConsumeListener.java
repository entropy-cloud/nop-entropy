/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core;

import java.util.List;

public interface IBatchConsumeListener<R, C> {
    default void onConsumeBegin(List<R> items, C context) {
    }

    default void onConsumeEnd(Throwable exception, List<R> items, C context) {
    }
}