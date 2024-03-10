/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core;

/**
 * onChunkBegin和onChunkEnd都是在事务范围之外执行
 */
public interface IBatchChunkListener {
    default void onChunkBegin(IBatchChunkContext context) {

    }

    default void onChunkEnd(Throwable exception, IBatchChunkContext context) {

    }
}
