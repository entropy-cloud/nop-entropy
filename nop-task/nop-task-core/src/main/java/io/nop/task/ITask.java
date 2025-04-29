/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task;

import io.nop.core.execution.IExecution;
import io.nop.xlang.xdsl.action.IActionInputModel;
import io.nop.xlang.xdsl.action.IActionBaseModel;
import io.nop.xlang.xdsl.action.IActionOutputModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public interface ITask extends IActionBaseModel {
    String getTaskName();

    long getTaskVersion();

    List<? extends IActionInputModel> getInputs();

    List<? extends IActionOutputModel> getOutputs();

    TaskStepReturn execute(ITaskRuntime taskRt, Set<String> outputNames);

    default TaskStepReturn execute(ITaskRuntime taskRt) {
        return execute(taskRt, null);
    }

    default IExecution<Map<String, Object>> asExecution(ITaskRuntime taskRt, Set<String> outputNames) {
        return cancelToken -> {
            if (cancelToken != null) {
                Consumer<String> onCancel = taskRt::cancel;
                cancelToken.appendOnCancel(onCancel);
                taskRt.addTaskCleanup(() -> cancelToken.removeOnCancel(onCancel));
            }
            return execute(taskRt, outputNames).asyncOutputs();
        };
    }
}