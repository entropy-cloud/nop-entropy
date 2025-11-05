/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm.model;

import io.nop.fsm.execution.IStateContainer;
import io.nop.fsm.model._gen._StateModel;

public class StateModel extends _StateModel implements IStateContainer {
    /**
     * 包含父id和当前id的全路径
     */
    private StateId fullStateId;

    private IStateContainer parentContainer;

    public StateModel() {

    }

    public IStateContainer getParentContainer() {
        return parentContainer;
    }

    public void setParentContainer(IStateContainer parentContainer) {
        this.parentContainer = parentContainer;
    }

    public StateId getFullStateId() {
        return fullStateId;
    }

    public void setFullStateId(StateId fullStateId) {
        this.fullStateId = fullStateId;
    }
}
