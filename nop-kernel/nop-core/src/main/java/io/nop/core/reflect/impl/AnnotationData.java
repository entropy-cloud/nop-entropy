/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.collections.IKeyedElement;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.reflect.IAnnotationData;
import io.nop.core.reflect.IFunctionModel;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AnnotationData implements IAnnotationData, Serializable, IKeyedElement {
    private static final long serialVersionUID = -8569940845083974979L;
    private static final String ATTR_VALUE = "value";

    private final String name;
    private final Map<String, Object> properties;

    public AnnotationData(String name, Map<String, Object> properties) {
        this.name = name;
        this.properties = (Map<String, Object>) CollectionHelper.toUnmodifiable(properties);
    }

    @Override
    public String key() {
        return name;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getPropertyNames() {
        if (properties == null)
            return Collections.emptySet();
        return properties.keySet();
    }

    @Override
    public Object getProperty(String name) {
        if (properties == null)
            return null;
        return properties.get(name);
    }

    public static Object getAnnotationValue(Annotation ann) {
        Method[] methods = ann.annotationType().getMethods();
        for (Method method : methods) {
            if (method.getName().equals(ATTR_VALUE)) {
                return normalizeAnnValue(ann, method);
            }
        }
        return null;
    }

    public static <E extends Annotation> Object getAnnotationValue(AnnotatedElement element,
                                                                   Class<E> annClass) {
        if (annClass == null) {
            return null;
        }
        Annotation ann = element.getAnnotation(annClass);
        if (ann == null)
            return null;
        return getAnnotationValue(ann);
    }

    public static <E extends Annotation> AnnotationData fromAnnotation(AnnotatedElement element, Class<E> annClass) {
        if (annClass == null) {
            return null;
        }
        Annotation ann = element.getAnnotation(annClass);
        if (ann == null)
            return null;
        return fromAnnotation(ann);
    }

    public static AnnotationData fromAnnotation(Annotation ann) {
        String name = ann.annotationType().getCanonicalName();

        Map<String, Object> values = new HashMap<>();

        Method[] methods = ann.annotationType().getMethods();
        for (Method method : methods) {
            if (method.isSynthetic())
                continue;
            if (method.isBridge())
                continue;

            if (method.getDeclaringClass() == Object.class)
                continue;

            if (method.getParameters().length != 0)
                continue;

            if (method.getName().equals("hashCode"))
                continue;

            Object value = normalizeAnnValue(ann, method);
            values.put(method.getName(), value);
        }
        AnnotationData annData = new AnnotationData(name, values);
        return annData;
    }

    static Object normalizeAnnValue(Annotation ann, Method method) {
        try {
            return method.invoke(ann, IFunctionModel.EMPTY_ARGS);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}