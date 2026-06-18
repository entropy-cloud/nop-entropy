package io.nop.task.ext.timeout;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ext.TaskExtConstants;
import io.nop.task.ext.TaskExtErrors;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.step.TimeoutTaskStepWrapper;

public class TimeoutTaskStepDecorator implements ITaskStepDecorator {

    public static final String DECORATOR_NAME = "timeout";

    @Override
    public ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel) {
        Long timeout = readLong(config, TaskExtConstants.ATTR_TIMEOUT_TIMEOUT);
        if (timeout == null) {
            throw invalidConfig(config, TaskExtConstants.ATTR_TIMEOUT_TIMEOUT, null,
                    "required config missing");
        }
        if (timeout <= 0) {
            throw invalidConfig(config, TaskExtConstants.ATTR_TIMEOUT_TIMEOUT, timeout,
                    "must be > 0");
        }
        return new TimeoutTaskStepWrapper(step, timeout);
    }

    private static Long readLong(TaskDecoratorModel config, String attrName) {
        Object value = config.prop_get(attrName);
        if (value == null) {
            return null;
        }
        try {
            return ConvertHelper.toLong(value);
        } catch (Exception e) {
            throw invalidConfig(config, attrName, value, "cannot be converted to long");
        }
    }

    private static NopException invalidConfig(TaskDecoratorModel config, String attrName, Object value, String reason) {
        return new NopException(TaskExtErrors.ERR_TASK_DECORATOR_INVALID_CONFIG)
                .param(TaskExtErrors.ARG_DECORATOR_NAME, DECORATOR_NAME)
                .param(TaskExtErrors.ARG_ATTR_NAME, attrName)
                .param(TaskExtErrors.ARG_ATTR_VALUE, value)
                .param(TaskExtErrors.ARG_REASON, reason);
    }
}
