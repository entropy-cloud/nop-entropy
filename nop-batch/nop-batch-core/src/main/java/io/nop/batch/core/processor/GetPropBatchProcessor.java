/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.core.processor;

import io.nop.api.core.util.Guard;
import io.nop.batch.core.IBatchProcessor;
import io.nop.core.reflect.bean.BeanPropHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.function.Consumer;

public class GetPropBatchProcessor<S, R, C> implements IBatchProcessor<S, R, C> {
    private final String propPath;
    private final boolean simple;

    public GetPropBatchProcessor(String propPath) {
        this.propPath = Guard.notEmpty(propPath, "propPath");
        this.simple = BeanPropHelper.isSimple(propPath);
    }

    @Override
    public void process(S item, Consumer<R> consumer, C context) {
        Object value = simple ? BeanTool.getProperty(item, propPath) : BeanTool.getComplexProperty(item, propPath);
        consumer.accept((R) value);
    }
}
