package io.nop.ai.agent.engine;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

public class NopAiAgentException extends NopException {
    private static final long serialVersionUID = 1L;

    public NopAiAgentException(String message) {
        super(message, null, true, true);
    }

    public NopAiAgentException(String message, Throwable cause) {
        super(message, cause, true, true);
    }

    public NopAiAgentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NopAiAgentException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    /** 协变返回（plan 356：链式 .param 后保持子类型，供 typed 赋值场景）。 */
    @Override
    public NopAiAgentException param(String name, Object value) {
        super.param(name, value);
        return this;
    }
}
