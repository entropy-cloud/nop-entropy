/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.directive;

import java.lang.annotation.Annotation;

public class DependsOnAnnotation implements DependsOn {
    static final String[] EMPTY_NAMES = new String[0];

    private String[] propNames = EMPTY_NAMES;

    public String[] propNames() {
        return propNames;
    }

    public void setPropNames(String[] propNames) {
        this.propNames = propNames;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return DependsOn.class;
    }
}