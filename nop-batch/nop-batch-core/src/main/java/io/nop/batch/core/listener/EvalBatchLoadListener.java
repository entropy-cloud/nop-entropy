package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchLoadListener;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;

public class EvalBatchLoadListener<S> implements IBatchLoadListener<S, IEvalContext> {
    private final IEvalFunction onBegin;
    private final IEvalFunction onEnd;

    public EvalBatchLoadListener(IEvalFunction onBegin, IEvalFunction onEnd) {
        this.onBegin = onBegin;
        this.onEnd = onEnd;
    }

    @Override
    public void onLoadBegin(int batchSize, IEvalContext context) {
        if (onBegin != null)
            onBegin.call2(null, batchSize, context, context.getEvalScope());
    }

    @Override
    public void onLoadEnd(Throwable ex, int batchSize, IEvalContext context) {
        if (onEnd != null) {
            onEnd.call3(null, ex, batchSize, context, context.getEvalScope());
        }
    }
}
