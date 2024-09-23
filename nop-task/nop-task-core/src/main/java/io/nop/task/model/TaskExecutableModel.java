/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.model;

import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.impl.FunctionArgument;
import io.nop.task.model._gen._TaskExecutableModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TaskExecutableModel extends _TaskExecutableModel {
    public TaskExecutableModel() {

    }

    public abstract String getType();


    public List<? extends IFunctionArgument> getInputsAsArgModels() {
        if (getInputs() == null)
            return Collections.emptyList();

        List<FunctionArgument> args = new ArrayList<>(getInputs().size());
        for (TaskInputModel input : getInputs()) {
            FunctionArgument arg = new FunctionArgument();
            arg.setName(input.getName());
            arg.setType(input.getType());
            args.add(arg);
        }
        return args;
    }
}
