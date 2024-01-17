/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.reflect.accessor;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.ReflectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.reflect.IAnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

public class FieldPropertyAccessor implements ISpecializedPropertyGetter, ISpecializedPropertySetter, IAnnotationSupport {
    static final Logger LOG = LoggerFactory.getLogger(FieldPropertyAccessor.class);

    private final Field field;

    public FieldPropertyAccessor(Field field) {
        this.field = field;
        ReflectionHelper.makeAccessible(field);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return field.getAnnotation(annotationClass);
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
