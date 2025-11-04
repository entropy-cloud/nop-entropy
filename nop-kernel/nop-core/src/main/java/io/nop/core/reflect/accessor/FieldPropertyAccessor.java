/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IAnnotatedElement;
import io.nop.core.reflect.IAnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class FieldPropertyAccessor implements ISpecializedPropertyGetter, ISpecializedPropertySetter,
        IAnnotationSupport, IAnnotatedElement {
    static final Logger LOG = LoggerFactory.getLogger(FieldPropertyAccessor.class);

    private final Field field;
    private Map<String, Annotation> annotations;

    public FieldPropertyAccessor(Field field) {
        this.field = field;
        ReflectionHelper.makeAccessible(field);
    }

    public Field getField() {
        return field;
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
        return makeAnnotations().values();
    }

    @Override
    public Annotation getAnnotationByName(String annotationClass) {
        return makeAnnotations().get(annotationClass);
    }

    private Map<String, Annotation> makeAnnotations() {
        if (annotations == null) {
            if (annotations == null)
                annotations = new HashMap<>();
            for (Annotation ann : field.getAnnotations()) {
                annotations.put(ann.annotationType().getCanonicalName(), ann);
            }
        }
        return annotations;
    }

    @Override
    public Object getProperty(Object obj, String propName, IEvalScope scope) {
        try {
            return field.get(obj);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void setProperty(Object obj, String propName, Object value, IEvalScope scope) {
        try {
            field.set(obj, value);
        } catch (Exception e) {
            LOG.error("nop.field-set-fail:field={},value={}", field, value, e);
            throw NopException.adapt(e);
        }
    }
}
