/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchProcessorProvider.IBatchProcessor;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.function.Consumer;

public class CastBatchProcessor<S, R> implements IBatchProcessor<S, R> {
    private final IGenericType type;

    public CastBatchProcessor(IGenericType type) {
        this.type = type;
    }

    @Override
    public void process(S item, Consumer<R> consumer, IBatchChunkContext context) {
        R value = BeanTool.castBeanToType(item, type);
        consumer.accept(value);
    }
}
