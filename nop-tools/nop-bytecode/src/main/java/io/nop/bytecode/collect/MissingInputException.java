package io.nop.bytecode.collect;

import io.nop.bytecode.NopBytecodeException;

import java.util.List;

/**
 * Thrown when a declared collection input (directory or jar) does not exist or is not of the
 * declared kind. The collection layer fails loudly on missing inputs instead of skipping them:
 * a silent skip would let a stale artifact set masquerade as a complete one downstream.
 */
public class MissingInputException extends NopBytecodeException {
    private static final long serialVersionUID = 1L;

    public MissingInputException(List<String> missing) {
        super("Missing collection inputs (paths that do not exist or have the wrong kind): " + missing);
    }
}
