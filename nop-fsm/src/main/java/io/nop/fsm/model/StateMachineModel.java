/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.fsm.model;

import io.nop.api.core.util.INeedInit;
import io.nop.fsm.FsmConstants;
import io.nop.fsm.execution.IStateContainer;
import io.nop.fsm.model._gen._StateMachineModel;

import java.util.HashMap;
import java.util.Map;

public class StateMachineModel extends _StateMachineModel implements INeedInit, IStateContainer {

    /**
     * 从stateValue映射到唯一的State模型对象。stateValue为保存到数据库中的值。 如果没有为state指定stateValue，则stateValue为fullStateId
     */
    private Map<String, StateModel> stateValueMap;

    private Map<String, StateModel> fullStateIdMap;

    public StateMachineModel() {

    }

    @Override
    public void init() {
        stateValueMap = new HashMap<>();
        fullStateIdMap = new HashMap<>();

        for (StateModel stateModel : getStates()) {
            stateModel.setParentContainer(this);
            initState(stateModel, null);
        }

        StateMachineValidator.INSTANCE.validate(this);
    }

    private void initState(StateModel stateModel, StateId parentId) {
        StateId stateId = parentId == null ? StateId.fromText(stateModel.getId())
                : parentId.subState(stateModel.getId());
        stateModel.setFullStateId(stateId);
        Object stateValue = stateModel.getStateValue();
        if (stateValue == null)
            stateValue = stateId;

        stateValueMap.put(stateValue.toString(), stateModel);
        fullStateIdMap.put(stateId.toString(), stateModel);

        for (StateModel subState : stateModel.getStates()) {
            subState.setParentContainer(stateModel);
            initState(subState, stateId);
        }
    }

    public StateModel getStateFromStateValue(Object stateValue) {
        if (stateValue == null)
            stateValue = FsmConstants.STATE_EMPTY;

        return stateValueMap.get(stateValue.toString());
    }

    public StateModel getStateFromFullId(StateId stateId) {
        return fullStateIdMap.get(stateId.toString());
    }

    public StateModel getParentState(StateId stateId) {
        return fullStateIdMap.get(stateId.getParentId());
    }
}