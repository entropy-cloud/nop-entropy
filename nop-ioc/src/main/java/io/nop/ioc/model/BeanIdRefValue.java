/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanIdRefValue;

public class BeanIdRefValue extends _BeanIdRefValue implements IBeanPropValue {
    public BeanIdRefValue() {

    }

    @Override
    public String getBeanValueType() {
        return "idref";
    }
}
