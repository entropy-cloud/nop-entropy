package io.nop.jq.runtime;

/**
 * Control-flow exception carrying a jq error value. Thrown by the error
 * builtins and by runtime type errors; caught by try/catch, which feeds
 * {@link #errorValue()} to the handler expression.
 */
public class JqRuntimeException extends RuntimeException {
    private final JqValue errorValue;

    public JqRuntimeException(String message) {
        this(message, JqString.of(message));
    }

    public JqRuntimeException(String message, JqValue errorValue) {
        super(message);
        this.errorValue = errorValue;
    }

    public JqRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.errorValue = JqString.of(message);
    }

    /** The jq value carried by this error (what catch receives as input). */
    public JqValue errorValue() {
        return errorValue;
    }

    /**
     * The jq error message: for string errors the string itself, otherwise the
     * canonical jq rendering of the error value (matches how jq prints errors).
     */
    public String errorMessage() {
        return errorValue.toString();
    }
}
