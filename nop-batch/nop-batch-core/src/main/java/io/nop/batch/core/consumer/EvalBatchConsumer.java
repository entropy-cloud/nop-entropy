package io.nop.batch.core.consumer;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchConsumerProvider;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.Collection;

public class EvalBatchConsumer<R> implements IBatchConsumerProvider.IBatchConsumer<R>, IBatchConsumerProvider<R> {
    private final IEvalFunction func;

    public EvalBatchConsumer(IEvalFunction func) {
        this.func = func;
    }

    @Override
    public IBatchConsumer<R> setup(IBatchTaskContext context) {
        return this;
    }

    @Override
    public void consume(Collection<R> records, IBatchChunkContext context) {
        func.call2(null, records, context, context.getEvalScope());
    }
}
