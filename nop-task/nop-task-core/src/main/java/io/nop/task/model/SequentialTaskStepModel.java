/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.model;

import io.nop.task.TaskConstants;
import io.nop.task.model._gen._SequentialTaskStepModel;

public class SequentialTaskStepModel extends _SequentialTaskStepModel{
    public SequentialTaskStepModel(){

    }

    @Override
    public String getType() {
        return TaskConstants.STEP_TYPE_SEQUENTIAL;
    }
}
