package io.nop.core.reflect;

import java.lang.annotation.Annotation;

public interface IAnnotationSupport {
    <T extends Annotation> T getAnnotation(Class<T> annotationClass);
}
