/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.task.ITaskStepRuntime;
import io.nop.task.TaskStepReturn;
import io.nop.xlang.exec.ObjFunctionHandle;
import jakarta.annotation.Nonnull;

import java.util.List;

public class InvokeTaskStep extends AbstractTaskStep {
    private String beanName;
    private String methodName;

    private List<String> argNames;

    // 缓存反射方法的查询结果
    private final ObjFunctionHandle funcHandle = new ObjFunctionHandle();

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setArgNames(List<String> argNames) {
        this.argNames = argNames;
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

        IEvalFunction method = funcHandle.getFunctionForObj(bean, methodName,
                errorCode -> new NopException(errorCode).loc(getLocation()), args);
        Object returnValue = method.invoke(bean, args, scope);
        return makeReturn(returnValue);
    }
}