/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._TaskChooseCaseModel;

public class TaskChooseCaseModel extends _TaskChooseCaseModel {
    public TaskChooseCaseModel() {

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_CASE;
    }

    @Override
    public Boolean getUseParentScope() {
        return true;
    }
}
