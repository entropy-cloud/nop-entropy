/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm.execution;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.fsm.FsmConstants;
import io.nop.fsm.model.StateMachineModel;
import io.nop.fsm.model.StateModel;
import io.nop.fsm.model.StateTransitionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.nop.fsm.FsmErrors.ARG_EVENT;
import static io.nop.fsm.FsmErrors.ARG_STATE_ID;
import static io.nop.fsm.FsmErrors.ARG_STATE_VALUE;
import static io.nop.fsm.FsmErrors.ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT;
import static io.nop.fsm.FsmErrors.ERR_FSM_UNDEFINED_STATE;
import static io.nop.fsm.FsmErrors.ERR_FSM_UNKNOWN_STATE_VALUE;

public class StateMachine implements IStateMachine {
    static final Logger LOG = LoggerFactory.getLogger(StateMachine.class);

    private final StateMachineModel model;

    public StateMachine(StateMachineModel model) {
        this.model = model;
    }

    @Override
    public void initState(Object bean) {
        StateModel stateModel = model.getState(model.getInitial());
        if (stateModel != null) {
            Object stateValue = stateModel.getStateValue();
            if (stateValue == null)
                stateValue = stateModel.getFullStateId().toString();

            BeanTool.setComplexProperty(bean, model.getStateProp(), stateValue);
        }
    }

    @Override
    public Object getInitStateValue() {
        StateModel stateModel = model.getState(model.getInitial());
        if (stateModel != null) {
            Object stateValue = stateModel.getStateValue();
            if (stateValue == null)
                stateValue = stateModel.getFullStateId().toString();

            return stateValue;
        }
        return model.getInitial();
    }

    @Override
    public String getEvent(String prefix, Object message, IEvalContext ctx) {
        if (message instanceof String) {
            if (prefix == null)
                return (String) message;
            return prefix + message;
        }
        String type;
        if (model.getMessageTypeGetter() != null) {
            type = ConvertHelper.toString(model.getMessageTypeGetter().call1(null, message, ctx.getEvalScope()));
        } else if (model.getMessageTypeProp() != null) {
            type = ConvertHelper.toString(BeanTool.getProperty(message, model.getMessageTypeProp()));
        } else {
            type = message.getClass().getSimpleName();
        }
        if (prefix == null)
            return String.valueOf(type);

        return prefix + type;
    }

    @Override
    public void triggerStateChange(Object bean, String event, IEvalContext ctx, Consumer<StateModel> action) {
        IEvalScope scope = ctx.getEvalScope();
        ValueWithLocation vl = scope.recordValueLocation(FsmConstants.VAR_ENTITY);
        scope.setLocalValue(FsmConstants.VAR_ENTITY, bean);

        try {
            Object stateValue = BeanTool.getComplexProperty(bean, model.getStateProp());

            transit(stateValue, event, ctx, (stateModel, value) -> {
                BeanTool.setComplexProperty(bean, model.getStateProp(), value);
                if (action != null)
                    action.accept(stateModel);
            });
        } finally {
            scope.restoreValueLocation(FsmConstants.VAR_ENTITY, vl);
        }
    }

    @Override
    public void transit(Object stateValue, String event, IEvalContext scope,
                        BiConsumer<StateModel, Object> onStateChange) {
        StateModel stateModel = model.getStateFromStateValue(stateValue);
        if (stateModel == null)
            throw new NopException(ERR_FSM_UNKNOWN_STATE_VALUE).param(ARG_STATE_VALUE, stateValue);

        doTransit(stateModel, event, scope, onStateChange);
    }

    private void doTransit(StateModel stateModel, String event, IEvalContext scope,
                           BiConsumer<StateModel, Object> onStateChange) {
        StateTransitionModel transition = getTransition(stateModel, event, scope);
        if (transition == null) {
            if (model.isIgnoreUnknownTransition())
                return;

            throw new NopException(ERR_FSM_STATE_NO_TRANSITION_FOR_EVENT)
                    .param(ARG_STATE_ID, stateModel.getFullStateId()).param(ARG_EVENT, event);
        }

        // 执行动作 invoke, onExit, actions, onEntry

        String target = null;

        try {
            if (transition.getInvoke() != null) {
                transition.getInvoke().invoke(scope);
            }

            target = transition.getTarget();

            if (target != null) {
                if (stateModel.getOnExit() != null) {
                    stateModel.getOnExit().invoke(scope);
                }
                invokeActions(stateModel.getExit(), scope);
            }

            invokeActions(transition.getActions(), scope);

        } catch (Exception e) {
            LOG.error("nop.fsm.state-machine-transition-fail", e);

            boolean handled = false;

            if (stateModel.getHandleError() != null) {
                handled = Boolean.TRUE.equals(stateModel.getHandleError().call1(null, e, scope.getEvalScope()));
                if (!handled)
                    throw NopException.adapt(e);
            }

            target = stateModel.getOnError();
            if (target == null && !handled)
                throw NopException.adapt(e);
        }

        if (target != null) {
            moveToNext(stateModel, target, scope, onStateChange);
        }
    }

    StateModel requireState(IStateContainer stateContainer, String stateId) {
        StateModel nextState = stateContainer.getState(stateId);
        if (nextState == null)
            throw new NopException(ERR_FSM_UNDEFINED_STATE).source(stateContainer).param(ARG_STATE_ID, stateId);
        return nextState;
    }

    private void invokeActions(Set<String> actions, IEvalContext scope) {
        if (actions == null || actions.isEmpty())
            return;

        LOG.debug("fsm.invoke-actions:actions={},scope={}", actions, scope.hashCode());

        IEvalFunction actionInvoker = model.getInvokeAction();
        if (actionInvoker != null) {
            for (String action : actions) {
                actionInvoker.call1(null, action, scope.getEvalScope());
            }
        }
    }

    private StateTransitionModel getTransition(StateModel stateModel, String event, IEvalContext scope) {
        for (StateTransitionModel transition : stateModel.getTransitions()) {
            if (transition.getEvent() != null) {
                if (!StringHelper.matchSimplePattern(event, transition.getEvent())) {
                    continue;
                }
            }

            if (transition.getWhen() == null || transition.getWhen().passConditions(scope)) {
                return transition;
            }
        }
        return null;
    }

    private StateModel enterState(StateModel stateModel, IEvalContext scope,
                                  BiConsumer<StateModel, Object> onStateChange) {

        if (stateModel.getOnEntry() != null) {
            stateModel.getOnEntry().invoke(scope);
        }

        if (stateModel.getInitial() != null) {
            StateModel subState = stateModel.getState(stateModel.getInitial());
            if (subState == null)
                throw new NopException(ERR_FSM_UNDEFINED_STATE).source(stateModel).param(ARG_STATE_ID,
                        stateModel.getInitial());

            StateModel nextState = enterState(subState, scope, onStateChange);
            return nextState == null ? subState : nextState;
        } else {
            Object stateValue = stateModel.getStateValue();
            if (stateValue == null)
                stateValue = stateModel.getFullStateId().toString();
            onStateChange.accept(stateModel, stateValue);
        }

        return null;
    }

    private void moveToNext(StateModel stateModel, String nextStateId, IEvalContext scope,
                            BiConsumer<StateModel, Object> onStateChange) {
        StateModel targetModel = requireState(stateModel.getParentContainer(), nextStateId);
        StateModel nextState = enterState(targetModel, scope, onStateChange);

        if (nextState != null && nextState.isFinal()) {
            String done = nextState.getOnDone();
            if (done != null && !nextState.getId().equals(done)) {
                moveToNext(nextState, done, scope, onStateChange);
            }
        }
    }
}