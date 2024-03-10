/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect;

import io.nop.api.core.annotations.core.Description;
import io.nop.core.reflect.impl.AnnotationData;

import java.lang.annotation.Annotation;
import java.util.Collection;

public interface IAnnotatedElement {
    Collection<Annotation> getAnnotations();

    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotationByName(annotationClass.getName()) != null;
    }

    default boolean isAnnotationPresentByName(String annotationClass) {
        return getAnnotationByName(annotationClass) != null;
    }

    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getAnnotationByName(annotationClass.getName());
    }

    <T extends Annotation> T getAnnotationByName(String annotationClass);

    default AnnotationData getAnnotationData(Class<? extends Annotation> annClass) {
        if (annClass == null)
            return null;
        Annotation ann = getAnnotation(annClass);
        if (ann == null)
            return null;
        return AnnotationData.fromAnnotation(ann);
    }

    default Object getAnnotationValue(Class<? extends Annotation> annClass) {
        if (annClass == null)
            return null;
        Annotation ann = getAnnotation(annClass);
        if (ann == null)
            return null;
        return AnnotationData.getAnnotationValue(ann);
    }

    default String getDescription() {
        Description desc = getAnnotation(Description.class);
        if (desc == null)
            return null;
        return desc.value();
    }
}