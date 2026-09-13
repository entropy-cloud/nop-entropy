package io.nop.ai.core;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * Module-level exception for nop-ai-core (W2 reliability sink-down,
 * plan 2026-08-15-0604-2): the nop-ai-core equivalent of
 * {@code io.nop.ai.agent.engine.NopAiAgentException}. Constructors mirror the
 * NopAiAgentException four-piece set so migrated reliability classes can be
 * rethrown unchanged. Error codes are carried via {@link NopAiCoreErrors}.
 */
public class NopAiCoreException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopAiCoreException(String message) {
        super(message, null, true, true);
    }

    public NopAiCoreException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopAiCoreException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopAiCoreException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /** 协变返回（plan 356：链式 .param 后保持子类型）。 */
    @Override
    public NopAiCoreException param(String name, Object value) {
        super.param(name, value);
        return this;
    }
}
