package io.nop.ib;

/**
 * Module-level exception for nop-ib internals. English messages per repo error-handling convention;
 * structured {@code NopException} + {@code ErrorCode} surfaces are reserved for the public CLI
 * feedback contract (design 05 §九), which maps internal failures onto explicit exit codes.
 */
public class NopIbException extends RuntimeException {
    public NopIbException(String message) {
        super(message);
    }

    public NopIbException(String message, Throwable cause) {
        super(message, cause);
    }
}
