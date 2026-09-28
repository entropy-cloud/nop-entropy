package io.nop.jq.jq.runtime;

import io.nop.jq.jq.ast.JqAstNode;

import java.util.List;

/**
 * A filter argument bound to a function parameter (def f(g): ...; f(.a)).
 * The argument AST is evaluated in the environment of the call site, with the
 * input at the point where the parameter is referenced.
 */
public final class JqClosureFn implements JqFunction {
    private final JqEnvironment capturedEnv;
    private final JqAstNode body;
    private final JqExecutor executor;

    public JqClosureFn(JqEnvironment capturedEnv, JqAstNode body, JqExecutor executor) {
        this.capturedEnv = capturedEnv;
        this.body = body;
        this.executor = executor;
    }

    @Override
    public JqValue apply(List<JqValue> args, JqValue input) {
        // closures reference zero-arg parameters; extra args are a caller error
        List<JqValue> outputs = executor.execute(body, input, capturedEnv);
        return outputs.isEmpty() ? JqValue.NULL : outputs.get(outputs.size() - 1);
    }

    /** Execute the captured argument expression, producing all outputs. */
    public void executeInto(JqValue input, List<JqValue> outputs) {
        executor.executeInto(body, input, capturedEnv, outputs);
    }

    /** The argument AST captured at the call site. */
    public JqAstNode body() {
        return body;
    }

    /** The environment captured at the call site. */
    public JqEnvironment env() {
        return capturedEnv;
    }

    @Override
    public String toString() {
        return "closure(" + body + ")";
    }
}
