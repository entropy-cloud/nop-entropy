/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanNullValue;

public class BeanNullValue extends _BeanNullValue implements IBeanPropValue {
    public BeanNullValue() {

    }

    @Override
    public String getBeanValueType() {
        return "null";
    }
}
