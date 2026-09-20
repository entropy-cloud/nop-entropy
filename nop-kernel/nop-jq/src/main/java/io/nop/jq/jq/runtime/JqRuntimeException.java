package io.nop.jq.jq.runtime;

/**
 * Runtime exception for jq execution errors.
 */
public class JqRuntimeException extends RuntimeException {
    private final String message;

    public JqRuntimeException(String message) {
        super(message);
        this.message = message;
    }

    public JqRuntimeException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /**
     * The jq error value carried by this exception. jq's try/catch feeds this
     * string into the catch filter as its input.
     */
    public String errorMessage() {
        return message;
    }
}
