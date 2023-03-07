/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.directive;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.INeedInit;

import java.lang.annotation.Annotation;

import static io.nop.api.core.ApiErrors.ARG_CLASS_NAME;
import static io.nop.api.core.ApiErrors.ARG_PROP_NAME;
import static io.nop.api.core.ApiErrors.ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY;

public class DictLabelAnnotation implements DictLabel, INeedInit {
    private String dictName = "";
    private String valuePropName = "";

    public String dictName() {
        return dictName;
    }

    public void setDictName(String dictName) {
        this.dictName = dictName;
    }

    public String dictValueProp() {
        return valuePropName;
    }

    public void setValuePropName(String valuePropName) {
        this.valuePropName = valuePropName;
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return DictLabel.class;
    }

    @Override
    public void init() {
        if (ApiStringHelper.isEmpty(dictName))
            throw new NopException(ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY)
                    .param(ARG_CLASS_NAME, annotationType().getName())
                    .param(ARG_PROP_NAME, "dictName");

        if (ApiStringHelper.isEmpty(valuePropName))
            throw new NopException(ERR_ANNOTATION_PROP_NOT_ALLOW_EMPTY)
                    .param(ARG_CLASS_NAME, annotationType().getName())
                    .param(ARG_PROP_NAME, "valuePropName");
    }
}
