/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IAnnotationSupport;
import io.nop.core.reflect.IFunctionModel;

import java.lang.annotation.Annotation;

public class FunctionSpecializedPropertySetter implements ISpecializedPropertySetter, IAnnotationSupport {
    private final IFunctionModel func;

    public FunctionSpecializedPropertySetter(IFunctionModel func) {
        this.func = func;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return func.getAnnotation(annotationClass);
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        func.call1(obj, value, scope);
    }
}
