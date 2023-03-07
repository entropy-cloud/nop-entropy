/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanCollectBeansValue;

public class BeanCollectBeansValue extends _BeanCollectBeansValue implements IBeanPropValue {
    public BeanCollectBeansValue() {

    }

    @Override
    public String getBeanValueType() {
        return "ioc:collect-beans";
    }
}
