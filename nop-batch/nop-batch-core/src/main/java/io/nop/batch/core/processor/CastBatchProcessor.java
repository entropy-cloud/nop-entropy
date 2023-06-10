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
