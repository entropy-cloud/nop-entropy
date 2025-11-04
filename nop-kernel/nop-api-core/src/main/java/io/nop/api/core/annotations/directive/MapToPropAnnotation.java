/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.annotations.directive;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.INeedInit;

import java.lang.annotation.Annotation;

import static io.nop.api.core.ApiErrors.ARG_CLASS_NAME;
import static io.nop.api.core.ApiErrors.ARG_PROP_NAME;
import static io.nop.api.core.ApiErrors.ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY;

public class MapToPropAnnotation implements MapToProp, INeedInit {
    private String propName = "";

    public String propName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return MapToProp.class;
    }

    @Override
    public void init() {
        if (ApiStringHelper.isEmpty(propName))
            throw new NopException(ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY)
                    .param(ARG_CLASS_NAME, annotationType().getName())
                    .param(ARG_PROP_NAME, "propName");
    }
}