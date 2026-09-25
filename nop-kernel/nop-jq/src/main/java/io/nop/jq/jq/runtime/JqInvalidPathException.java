package io.nop.jq.jq.runtime;

/**
 * Raised when a filter used as a path expression produces a plain value
 * instead of a path. Carries the offending value for jq-style error text.
 */
public class JqInvalidPathException extends JqRuntimeException {
    private final JqValue value;

    public JqInvalidPathException(String message, JqValue value) {
        super(message, JqString.of(message));
        this.value = value;
    }

    public JqValue value() {
        return value;
    }
}
