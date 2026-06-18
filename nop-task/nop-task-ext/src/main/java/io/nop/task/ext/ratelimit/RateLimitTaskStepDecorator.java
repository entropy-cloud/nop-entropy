package io.nop.task.ext.ratelimit;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.task.ITaskStep;
import io.nop.task.ITaskStepDecorator;
import io.nop.task.ext.TaskExtConstants;
import io.nop.task.ext.TaskExtErrors;
import io.nop.task.model.TaskDecoratorModel;
import io.nop.task.model.TaskStepModel;
import io.nop.task.step.RateLimitTaskStepWrapper;

public class RateLimitTaskStepDecorator implements ITaskStepDecorator {

    public static final String DECORATOR_NAME = "rateLimit";

    @Override
    public ITaskStep decorate(ITaskStep step, TaskDecoratorModel config, TaskStepModel stepModel) {
        Double requestPerSecond = readDouble(config, TaskExtConstants.ATTR_RATE_LIMIT_REQUEST_PER_SECOND);
        if (requestPerSecond == null) {
            throw invalidConfig(config, TaskExtConstants.ATTR_RATE_LIMIT_REQUEST_PER_SECOND, null,
                    "required config missing");
        }
        if (requestPerSecond <= 0) {
            throw invalidConfig(config, TaskExtConstants.ATTR_RATE_LIMIT_REQUEST_PER_SECOND, requestPerSecond,
                    "must be > 0");
        }

        boolean global = readBoolean(config, TaskExtConstants.ATTR_RATE_LIMIT_GLOBAL, false);

        Integer maxWait = readInt(config, TaskExtConstants.ATTR_RATE_LIMIT_MAX_WAIT);
        int resolvedMaxWait = maxWait == null ? 0 : maxWait;
        if (resolvedMaxWait < 0) {
            throw invalidConfig(config, TaskExtConstants.ATTR_RATE_LIMIT_MAX_WAIT, resolvedMaxWait,
                    "must be >= 0");
        }

        IEvalAction keyExpr = null;
        Object keyExprValue = config.prop_get("rateLimit:keyExpr");
        if (keyExprValue instanceof IEvalAction) {
            keyExpr = (IEvalAction) keyExprValue;
        } else if (keyExprValue instanceof String) {
            throw invalidConfig(config, "rateLimit:keyExpr", keyExprValue,
                    "rateLimit:keyExpr must be an xpl expression, not a plain string");
        }

        return new RateLimitTaskStepWrapper(step, requestPerSecond, global, resolvedMaxWait, keyExpr);
    }

    private static Double readDouble(TaskDecoratorModel config, String attrName) {
        Object value = config.prop_get(attrName);
        if (value == null) {
            return null;
        }
        try {
            return ConvertHelper.toDouble(value);
        } catch (Exception e) {
            throw invalidConfig(config, attrName, value, "cannot be converted to double");
        }
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

    private static boolean readBoolean(TaskDecoratorModel config, String attrName, boolean defaultValue) {
        Object value = config.prop_get(attrName);
        if (value == null) {
            return defaultValue;
        }
        try {
            Boolean result = ConvertHelper.toBoolean(value);
            return result == null ? defaultValue : result;
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
