package io.nop.api.core.auth;

import io.nop.api.core.util.IVariableScope;

public interface ISecurityContext {
    IVariableScope getEvalScope();

    IUserContext getUserContext();

}