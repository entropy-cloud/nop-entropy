package io.nop.core.reflect.accessor;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IAnnotationSupport;
import io.nop.core.reflect.IFunctionModel;

import java.lang.annotation.Annotation;

public class MethodPropertySetter implements ISpecializedPropertySetter, IAnnotationSupport {
    private final IFunctionModel method;

    public MethodPropertySetter(IFunctionModel method) {
        this.method = method;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return method.getAnnotation(annotationClass);
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        method.call1(obj, value, scope);
    }
}
