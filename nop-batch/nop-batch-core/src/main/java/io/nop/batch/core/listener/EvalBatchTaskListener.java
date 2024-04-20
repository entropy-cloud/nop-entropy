package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.IBatchTaskListener;
import io.nop.core.lang.eval.IEvalFunction;

public class EvalBatchTaskListener implements IBatchTaskListener {
    private final IEvalFunction onBegin;
    private final IEvalFunction onEnd;

    public EvalBatchTaskListener(IEvalFunction onBegin, IEvalFunction onEnd) {
        this.onBegin = onBegin;
        this.onEnd = onEnd;
    }

    @Override
    public void onTaskBegin(IBatchTaskContext context) {
        if (onBegin != null)
            onBegin.call1(null, context, context.getEvalScope());
    }

    @Override
    public void onTaskEnd(Throwable exception, IBatchTaskContext context) {
        if (onEnd != null)
            onEnd.call2(null, exception, context, context.getEvalScope());
    }
}