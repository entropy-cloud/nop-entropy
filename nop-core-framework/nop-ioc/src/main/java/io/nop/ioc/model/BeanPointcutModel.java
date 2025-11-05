/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.ioc.api.IBeanPointcut;
import io.nop.ioc.model._gen._BeanPointcutModel;

import java.util.List;

public class BeanPointcutModel extends _BeanPointcutModel implements IBeanPointcut {
    private List<Class<?>> annotationClasses;

    public BeanPointcutModel() {

    }

    public List<Class<?>> getAnnotationClasses() {
        return annotationClasses;
    }

    public void setAnnotationClasses(List<Class<?>> annotationClasses) {
        this.annotationClasses = annotationClasses;
    }
}
