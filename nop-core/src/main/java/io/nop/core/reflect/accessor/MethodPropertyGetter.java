package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IAnnotationSupport;
import io.nop.core.reflect.IFunctionModel;

import java.lang.annotation.Annotation;

public class MethodPropertyGetter implements ISpecializedPropertyGetter, IAnnotationSupport {
    private final IFunctionModel method;

    public MethodPropertyGetter(IFunctionModel method) {
        this.method = method;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return method.getAnnotation(annotationClass);
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        return method.call0(obj, scope);
    }
}
