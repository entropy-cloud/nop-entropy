package io.nop.lint.js.tsc;

import io.nop.lint.js.NopLintJsException;

/**
 * A tsc bridge query failed with a structured outcome (design 06 §5.3 error
 * frames): the wire protocol's error frame code, a query deadline that
 * expired, or a resident process that died mid-conversation. Callers treat
 * this as a per-query failure — the bridge may still recover through its
 * bounded restart policy — never as a reason to fabricate a result.
 */
public final class TscQueryException extends NopLintJsException {

    private final String code;

    public TscQueryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public TscQueryException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * The structured failure code (wire error-frame code, or {@code TIMEOUT}
     * / {@code BRIDGE_CRASHED} for the process-level failures the protocol
     * could not answer).
     */
    public String code() {
        return code;
    }
}
