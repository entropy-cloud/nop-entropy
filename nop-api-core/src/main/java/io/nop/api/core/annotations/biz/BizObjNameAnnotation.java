/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.biz;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.INeedInit;

import java.lang.annotation.Annotation;

import static io.nop.api.core.ApiErrors.ARG_CLASS_NAME;
import static io.nop.api.core.ApiErrors.ARG_PROP_NAME;
import static io.nop.api.core.ApiErrors.ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY;

public class BizObjNameAnnotation implements BizObjName, INeedInit {
    private String value = "";

    public String value() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return BizObjName.class;
    }

    @Override
    public void init() {
        if (ApiStringHelper.isEmpty(value))
            throw new NopException(ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY)
                    .param(ARG_CLASS_NAME, annotationType().getName())
                    .param(ARG_PROP_NAME, "value");
    }
}