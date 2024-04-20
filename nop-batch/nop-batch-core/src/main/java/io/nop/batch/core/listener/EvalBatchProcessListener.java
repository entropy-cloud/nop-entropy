package io.nop.batch.core.listener;

import io.nop.batch.core.IBatchProcessListener;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;

import java.util.function.Consumer;

public class EvalBatchProcessListener<S, R> implements IBatchProcessListener<S, R, IEvalContext> {
    private final IEvalFunction onBegin;
    private final IEvalFunction onEnd;

    public EvalBatchProcessListener(IEvalFunction onBegin, IEvalFunction onEnd) {
        this.onBegin = onBegin;
        this.onEnd = onEnd;
    }

    @Override
    public void onProcessBegin(S item, Consumer<R> consumer, IEvalContext context) {
        if (onBegin != null)
            onBegin.call3(null, item, consumer, context, context.getEvalScope());
    }

    @Override
    public void onProcessEnd(Throwable e, S item, Consumer<R> consumer, IEvalContext context) {
        if (onEnd != null) {
            onEnd.invoke(null, new Object[]{e, item, consumer, context}, context.getEvalScope());
        }
    }
}
