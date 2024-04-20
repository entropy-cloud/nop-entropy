package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchChunkContext;
import io.nop.batch.core.IBatchChunkListener;
import io.nop.core.lang.eval.IEvalFunction;

public class EvalBatchChunkListener implements IBatchChunkListener {
    private final IEvalFunction onBegin;
    private final IEvalFunction onEnd;

    public EvalBatchChunkListener(IEvalFunction onBegin, IEvalFunction onEnd) {
        this.onBegin = onBegin;
        this.onEnd = onEnd;
    }

    @Override
    public void onChunkBegin(IBatchChunkContext context) {
        if (onBegin != null)
            onBegin.call1(null, context, context.getEvalScope());
    }

    @Override
    public void onChunkEnd(Throwable exception, IBatchChunkContext context) {
        if (onEnd != null)
            onEnd.call2(null, exception, context, context.getEvalScope());
    }
}
