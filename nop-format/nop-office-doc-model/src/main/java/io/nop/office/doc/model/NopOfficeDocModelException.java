package io.nop.office.doc.model;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Module-level exception for the office doc model layer (English messages, see
 * docs-for-ai/02-core-guides/error-handling.md two-tier rule).
 */
public class NopOfficeDocModelException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopOfficeDocModelException(String message) {
        super(message, null, true, true);
    }

    public NopOfficeDocModelException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopOfficeDocModelException(ErrorCode errorCode) {
        super(errorCode);
    }
}
