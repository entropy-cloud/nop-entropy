/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.builder.functions;

import io.nop.core.lang.eval.IEvalFunction;
import io.nop.stream.core.exceptions.StreamException;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_ARG_NAME;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NULL_ARG;
import io.nop.stream.core.common.functions.source.SourceFunction;
/**
 * Adapts a parsed {@code <source><source>xpl-fn:(ctx)=>void</source></source>} body to a
 * {@link SourceFunction}.
 *
 * <p>The xpl body receives the runtime {@link SourceFunction.SourceContext} as its single
 * argument and pushes elements into the context. The documented cancellation pattern is
 * polling the context's cancel accessor:
 * <pre>{@code
 * while (!ctx.isCancelled()) {
 *     // read from the external system, then
 *     ctx.collect(element);
 * }
 * }</pre>
 * The engine wires the context's {@code isCancelled()} to the task mailbox's cancel flag,
 * so the body observes the same cooperative cancel signal that surfaces as the
 * checkpoint-abort exception inside {@code collect()}. Either exit path (loop-condition
 * polling, or the cooperative exception on the next collect after cancel) is valid.
 *
 * <p>{@link #cancel()} flips the wrapper's own {@code volatile} flag: it is invoked on
 * the operator close path ({@code StreamSourceOperator.close()}) and kept as the test
 * surface ({@link #isRunning()}); {@link #run()} does not read it, because the body
 * cannot reach the wrapper's state — cancellation must be observed through the context.
 */
public final class XplSourceFunction<T> implements SourceFunction<T> {

    private static final long serialVersionUID = 1L;

    private final IEvalFunction body;

    private volatile boolean running = true;

    public XplSourceFunction(IEvalFunction body) {
        if (body == null) {
            throw new StreamException(ERR_STREAM_NULL_ARG).param(ARG_ARG_NAME, "xpl SourceFunction body");
        }
        this.body = body;
    }

    @Override
    public void run(SourceFunction.SourceContext<T> ctx) {
        // The xpl body observes cancellation through the context: it polls
        // ctx.isCancelled() (production context reflects the task mailbox cancel flag)
        // and/or unwinds via the cooperative exception that collect() throws after
        // cancel. The wrapper's own `running` flag is not observable from the body.
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
