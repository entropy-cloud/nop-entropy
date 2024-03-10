/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.model;

import io.nop.api.core.util.INeedInit;
import io.nop.biz.api.IBizModel;
import io.nop.biz.model._gen._BizModel;
import io.nop.fsm.model.StateMachineModel;

public class BizModel extends _BizModel implements IBizModel, INeedInit {
    public BizModel() {

    }

    @Override
    public void init() {
        StateMachineModel stm = getStateMachine();
        if (stm != null)
            stm.init();

        for (BizActionModel actionModel : getActions()) {
            actionModel.init();
        }
    }
}
