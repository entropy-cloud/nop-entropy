/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.impl;

import io.nop.core.lang.utils.ReadonlyModel;
import io.nop.core.reflect.IAnnotatedElement;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AnnotatedElement extends ReadonlyModel implements IAnnotatedElement {
    private static final long serialVersionUID = -3448788837968873238L;

    private Map<String, Annotation> annotations = Collections.emptyMap();

    public AnnotatedElement() {
    }

    public void addAnnotation(Annotation ann) {
        checkReadonly();
        if (annotations.isEmpty())
            annotations = new HashMap<>();
        annotations.put(ann.annotationType().getCanonicalName(), ann);
    }

    public void addAnnotations(Annotation[] anns) {
        for (Annotation ann : anns) {
            addAnnotation(ann);
        }
    }

    public void addAnnotations(Collection<Annotation> anns) {
        for (Annotation ann : anns) {
            addAnnotation(ann);
        }
    }

    public Collection<Annotation> getAnnotations() {
        return Collections.unmodifiableCollection(annotations.values());
    }

    @Override
    public Annotation getAnnotationByName(String annotationClass) {
        return annotations.get(annotationClass);
    }
}