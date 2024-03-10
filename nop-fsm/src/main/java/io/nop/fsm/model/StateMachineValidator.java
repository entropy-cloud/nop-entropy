/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm.model;

import io.nop.api.core.exceptions.NopException;

import java.util.List;

import static io.nop.fsm.FsmErrors.ARG_STATE_ID;
import static io.nop.fsm.FsmErrors.ERR_FSM_UNDEFINED_STATE;

public class StateMachineValidator {
    public static final StateMachineValidator INSTANCE = new StateMachineValidator();

    public void validate(StateMachineModel stm) {
        if (stm.getInitial() != null) {
            StateModel state = stm.getState(stm.getInitial());
            if (state == null)
                throw new NopException(ERR_FSM_UNDEFINED_STATE).source(stm).param(ARG_STATE_ID, stm.getInitial());

            validateStates(stm, null, stm.getStates());
        }
    }

    private void validateStates(StateMachineModel stm, StateModel parentState, List<StateModel> states) {
        if (states != null) {
            for (StateModel state : states) {
                checkExistsSubState(state, state.getInitial());
                checkExistsParentState(stm, parentState, state.getOnDone());
                checkExistsParentState(stm, parentState, state.getOnError());

                validateStates(stm, state, state.getStates());
            }
        }
    }

    private void checkExistsSubState(StateModel state, String stateId) {
        if (stateId == null)
            return;

        StateModel subState = state.getState(stateId);
        if (subState == null)
            throw new NopException(ERR_FSM_UNDEFINED_STATE).source(state).param(ARG_STATE_ID, stateId);
    }

    private void checkExistsParentState(StateMachineModel stm, StateModel parentState, String stateId) {
        if (stateId == null)
            return;

        StateModel state = parentState == null ? stm.getState(stateId) : parentState.getState(stateId);
        if (state == null)
            throw new NopException(ERR_FSM_UNDEFINED_STATE).source(parentState == null ? stm : parentState)
                    .param(ARG_STATE_ID, stateId);
    }
}
