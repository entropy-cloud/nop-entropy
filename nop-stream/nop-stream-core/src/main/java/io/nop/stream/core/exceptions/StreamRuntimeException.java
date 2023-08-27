package io.nop.stream.core.exceptions;

public class StreamRuntimeException extends RuntimeException {
    public StreamRuntimeException(String message) {
        super(message);
    }

    public StreamRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
