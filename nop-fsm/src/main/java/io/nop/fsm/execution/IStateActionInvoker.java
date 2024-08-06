package io.nop.fsm.execution;

import io.nop.core.context.IEvalContext;

public interface IStateActionInvoker {
    void invoke(String action, IEvalContext scope);
}
