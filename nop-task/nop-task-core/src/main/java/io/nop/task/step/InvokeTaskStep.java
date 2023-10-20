/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.task.step;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreErrors;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.task.ITaskContext;
import io.nop.task.ITaskStepState;
import io.nop.task.TaskStepResult;

import java.util.List;

import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.core.CoreErrors.ARG_COUNT;
import static io.nop.core.CoreErrors.ARG_METHOD_NAME;

public class InvokeTaskStep extends AbstractTaskStep {
    private String beanName;
    private String methodName;

    private List<IEvalAction> argExprs;

    private boolean ignoreReturn;

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setArgExprs(List<IEvalAction> argExprs) {
        this.argExprs = argExprs;
    }

    public void setIgnoreReturn(boolean ignoreReturn) {
        this.ignoreReturn = ignoreReturn;
    }

    @Override
    protected TaskStepResult doExecute(ITaskStepState state, ITaskContext context) {
        IEvalScope scope = state.getEvalScope();
        Object bean = scope.getBeanProvider().getBean(beanName);
        Object[] args = new Object[argExprs.size()];
        for (int i = 0, n = argExprs.size(); i < n; i++) {
            args[i] = argExprs.get(i).invoke(scope);
        }

        IClassModel classModel = ReflectionManager.instance().getClassModel(bean.getClass());
        IFunctionModel method = classModel.getMethod(methodName, args.length);
        if (method == null)
            throw new NopException(CoreErrors.ERR_REFLECT_NO_METHOD_FOR_GIVEN_NAME_AND_ARG_COUNT)
                    .param(ARG_CLASS_NAME, classModel.getClassName())
                    .param(ARG_METHOD_NAME, methodName)
                    .param(ARG_COUNT, args.length);

        Object returnValue = method.invoke(bean, args, scope);
        if (ignoreReturn)
            returnValue = null;

        return toStepResult(returnValue);
    }
}
