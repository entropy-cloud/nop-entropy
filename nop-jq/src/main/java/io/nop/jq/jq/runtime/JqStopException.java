package io.nop.jq.jq.runtime;

/**
 * Internal control-flow exception used to stop output generation early,
 * e.g. when limit/first has enough outputs or any/all found its answer.
 * Must never escape the executor: lazy builtins catch it immediately.
 */
public final class JqStopException extends RuntimeException {
    public static final JqStopException INSTANCE = new JqStopException();

    private JqStopException() {
        super(null, null, false, false);
    }
}
