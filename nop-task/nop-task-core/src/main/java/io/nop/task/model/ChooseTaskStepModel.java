/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.api.core.util.INeedInit;
import io.nop.task.TaskConstants;
import io.nop.task.model._gen._ChooseTaskStepModel;

public class ChooseTaskStepModel extends _ChooseTaskStepModel implements INeedInit {
    public ChooseTaskStepModel() {

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_CHOOSE;
    }


    @Override
    public void init() {
        int i = 0;
        for (TaskChooseCaseModel caseModel : this.getCases()) {
            i++;
            caseModel.setName("case" + i);
            caseModel.setUseParentScope(true);

            caseModel.init();
        }

        if (this.getOtherwise() != null) {
            this.getOtherwise().setUseParentScope(true);
            this.getOtherwise().setName("otherwise");
            this.getOtherwise().init();
        }
    }

}
