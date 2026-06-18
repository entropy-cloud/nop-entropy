package io.nop.task.ext.retry;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.retry.RetryPolicy;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.ext.TaskExtConstants;
import io.nop.task.ext.TaskExtErrors;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.step.RetryTaskStepWrapper;

public class RetryTaskStepDecorator implements ITaskStepDecorator {

    public static final String DECORATOR_NAME = "retry";

    @Override
    public ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel) {
        Integer maxRetryCountRaw = readInt(config, TaskExtConstants.ATTR_RETRY_MAX_RETRY_COUNT);
        if (maxRetryCountRaw == null) {
            throw invalidConfig(config, TaskExtConstants.ATTR_RETRY_MAX_RETRY_COUNT, null,
                    "required config missing");
        }
        int maxRetryCount = maxRetryCountRaw;
        if (maxRetryCount < 0) {
            throw invalidConfig(config, TaskExtConstants.ATTR_RETRY_MAX_RETRY_COUNT, maxRetryCount,
                    "must be >= 0");
        }

        RetryPolicy<ITaskStepRuntime> policy = new RetryPolicy<>();
        policy.setMaxRetryCount(maxRetryCount);

        Integer retryDelay = readInt(config, TaskExtConstants.ATTR_RETRY_RETRY_DELAY);
        if (retryDelay != null) {
            if (retryDelay < 0) {
                throw invalidConfig(config, TaskExtConstants.ATTR_RETRY_RETRY_DELAY, retryDelay,
                        "must be >= 0");
            }
            policy.setRetryDelay(retryDelay);
        }

        Integer maxRetryDelay = readInt(config, TaskExtConstants.ATTR_RETRY_MAX_RETRY_DELAY);
        if (maxRetryDelay != null) {
            if (maxRetryDelay < 0) {
                throw invalidConfig(config, TaskExtConstants.ATTR_RETRY_MAX_RETRY_DELAY, maxRetryDelay,
                        "must be >= 0");
            }
            policy.setMaxRetryDelay(maxRetryDelay);
        }

        Boolean exponentialDelay = readBoolean(config, TaskExtConstants.ATTR_RETRY_EXPONENTIAL_DELAY);
        if (exponentialDelay != null) {
            policy.setExponentialDelay(exponentialDelay);
        }

        return new RetryTaskStepWrapper(step, policy);
    }

    private static Integer readInt(TaskDecoratorModel config, String attrName) {
        Object value = config.prop_get(attrName);
        if (value == null) {
            return null;
        }
        try {
            return ConvertHelper.toInteger(value,
                    err -> new NopException(err).param(TaskExtErrors.ARG_ATTR_NAME, attrName));
        } catch (Exception e) {
            throw invalidConfig(config, attrName, value, "cannot be converted to int");
        }
    }

    private static Boolean readBoolean(TaskDecoratorModel config, String attrName) {
        Object value = config.prop_get(attrName);
        if (value == null) {
            return null;
        }
        try {
            return ConvertHelper.toBoolean(value);
        } catch (Exception e) {
            throw invalidConfig(config, attrName, value, "cannot be converted to boolean");
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
