package io.nop.bytecode;

/**
 * Module-level exception for nop-bytecode internals (English messages per platform
 * error-handling convention for module internals).
 *
 * <p>Extends {@link RuntimeException} directly by adjudication (plan 02): the module keeps
 * zero {@code nop-*} runtime dependencies, so the platform {@code NopException} / ErrorCode
 * hierarchy is deliberately not used. This deviates from the nop-treesitter precedent
 * ({@code TreeSitterException extends NopException}); revisit via the channel-layer
 * adjudication (plan 04) if an ErrorCode surface is ever needed.
 */
public class NopBytecodeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NopBytecodeException(String message) {
        super(message);
    }

    public NopBytecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
