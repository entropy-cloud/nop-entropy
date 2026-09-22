package io.nop.lint.js.tsc;

import io.nop.lint.js.NopLintJsException;

/**
 * The explicit terminal-unavailable state of the tsc bridge (design 11 §5
 * degrade ladder, level 2): the Node/typescript environment is missing, the
 * handshake failed deterministically, or the bounded restart budget is
 * exhausted. Once raised, the bridge refuses further queries instead of
 * retrying forever; supply-side callers translate this into the engine's
 * degraded accounting — never into a silent skip and never into an L1
 * result pretending to be type-level.
 */
public final class TscBridgeUnavailableException extends NopLintJsException {

    public TscBridgeUnavailableException(String message) {
        super(message);
    }

    public TscBridgeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
