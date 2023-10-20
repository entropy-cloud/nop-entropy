/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.batch.core.processor;

import io.nop.batch.core.IBatchProcessor;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;

import java.util.function.Consumer;

public class CastBatchProcessor<S, R, C> implements IBatchProcessor<S, R, C> {
    private final IGenericType type;

    public CastBatchProcessor(IGenericType type) {
        this.type = type;
    }

    @Override
    public void process(S item, Consumer<R> consumer, C context) {
        R value = BeanTool.castBeanToType(item, type);
        consumer.accept(value);
    }
}
