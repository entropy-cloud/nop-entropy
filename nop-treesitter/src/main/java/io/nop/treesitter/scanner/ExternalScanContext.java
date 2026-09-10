package io.nop.treesitter.scanner;

/**
 * The lexer view an {@link ExternalScanner} drives (the pure-Java counterpart
 * of the C {@code TSLexer}): current lookahead, advance/mark_end, eof, byte
 * column and the result-symbol slot.
 */
public interface ExternalScanContext {

    /**
     * The current lookahead codepoint, or 0 at end of input.
     */
    int lookahead();

    /**
     * True at end of input.
     */
    boolean eof();

    /**
     * Consumes the current lookahead; {@code skip} moves the token start
     * forward too (C {@code lexer->advance(lexer, true)}).
     */
    void advance(boolean skip);

    /**
     * Marks the current position as the token end (C {@code mark_end}).
     */
    void markEnd();

    /**
     * The 0-based byte column of the current position (C {@code get_column}).
     */
    int getColumn();

    /**
     * Selects the recognized external token ordinal.
     */
    void setResultSymbol(int ordinal);
}
