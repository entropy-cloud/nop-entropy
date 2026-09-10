package io.nop.treesitter.scanner;

/**
 * A Java-implemented external scanner (the pure-Java counterpart of a C
 * {@code scanner.c}): hand translations for grammars whose external tokens
 * need cross-call state (e.g. Python's indentation stack) that the bytecode
 * DSL cannot express.
 *
 * <p>The contract mirrors the C ABI: {@link #scan} is invoked with the valid
 * external-symbol ordinals for the current parse state and drives the lexer
 * through {@link ExternalScanContext}; {@link #serialize}/{@link #deserialize}
 * carry the scanner's opaque state across calls (C serialize/deserialize).</p>
 */
public interface ExternalScanner {

    /**
     * Scans for the next external token. Returns {@code true} when a token was
     * recognized ({@link ExternalScanContext#setResultSymbol} must have been
     * called with a valid ordinal and {@code markEnd} with its end).
     */
    boolean scan(ExternalScanContext ctx, boolean[] validSymbols);

    /**
     * The scanner's serializable state (opaque bytes, C serialize contract).
     */
    byte[] serialize();

    /**
     * Restores previously serialized state.
     */
    void deserialize(byte[] state);
}
