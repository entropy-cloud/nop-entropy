/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreErrors;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_COUNT;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;

public class InvokeTaskStep extends AbstractTaskStep {
    private String beanName;
    private String methodName;

    private List<String> argNames;

    private String returnAs;

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setArgNames(List<String> argNames) {
        this.argNames = argNames;
    }

    public String getReturnAs() {
        return returnAs;
    }

    public void setReturnAs(String returnAs) {
        this.returnAs = returnAs;
    }

    @Nonnull
    @Override
    public TaskStepReturn execute(ITaskStepRuntime stepRt) {
        IEvalScope scope = stepRt.getEvalScope();
        Object bean = scope.getBeanProvider().getBean(beanName);
        Object[] args = new Object[argNames.size()];
        for (int i = 0, n = argNames.size(); i < n; i++) {
            args[i] = scope.getValue(argNames.get(i));
        }

        IClassModel classModel = ReflectionManager.instance().getClassModel(bean.getClass());
        IFunctionModel method = classModel.getMethod(methodName, args.length);
        if (method == null)
            throw new NopException(CoreErrors.ERR_REFLECT_NO_METHOD_FOR_GIVEN_NAME_AND_ARG_COUNT)
                    .param(ARG_CLASS_NAME, classModel.getClassName())
                    .param(ARG_METHOD_NAME, methodName)
                    .param(ARG_COUNT, args.length);

        Object returnValue = method.invoke(bean, args, scope);
        if (StringHelper.isEmpty(returnAs)) {
            return TaskStepReturn.of(null, returnValue);
        } else {
            if (returnValue instanceof CompletionStage) {
                return TaskStepReturn.of(null, ((CompletionStage) returnValue).thenApply(
                        v -> Collections.singletonMap(returnAs, v)));
            } else {
                return TaskStepReturn.RETURN(Collections.singletonMap(returnAs, returnValue));
            }
        }
    }
}