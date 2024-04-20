package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchConsumeListener;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.List;

public class EvalBatchConsumeListener<S> implements IBatchConsumeListener<S, IEvalContext> {
    private final IEvalFunction onBegin;
    private final IEvalFunction onEnd;

    public EvalBatchConsumeListener(IEvalFunction onBegin, IEvalFunction onEnd) {
        this.onBegin = onBegin;
        this.onEnd = onEnd;
    }

    @Override
    public void onConsumeBegin(List<S> items, IEvalContext context) {
        if (onBegin != null)
            onBegin.call2(null, items, context, context.getEvalScope());
    }

    @Override
    public void onConsumeEnd(Throwable exception, List<S> items, IEvalContext context) {
        if (onEnd != null)
            onEnd.call3(null, exception, items, context, context.getEvalScope());
    }
}
