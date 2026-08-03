/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
/**
 * Adapts a parsed {@code <source><source>xpl-fn:(ctx)=>void</source></source>} body to a
 * {@link SourceFunction}.
 *
 * <p>The xpl body receives the runtime {@link SourceFunction.SourceContext} as its single
 * argument and is expected to push elements into the context (typically via a
 * {@code while (running)} loop reading from an external system). Cancellation is signalled
 * by a {@code volatile} flag that the body must check (the wrapper does not interrupt the
 * thread).
 */
public final class XplSourceFunction<T> implements SourceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    private volatile boolean running = true;

    public XplSourceFunction(IEvalFunction body) {
        if (body == null) {
            throw new IllegalArgumentException("XplSourceFunction body must not be null");
        }
        this.body = body;
    }

    @Override
    public void run(SourceFunction.SourceContext<T> ctx) {
        // The xpl body is responsible for honouring the cancel flag and exiting its loop.
        body.call1(null, ctx, XplFunctionSupport.newCallScope());
    }

    @Override
    public void cancel() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
