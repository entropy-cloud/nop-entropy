/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanMapValue;

import java.util.function.Consumer;

public class BeanMapValue extends _BeanMapValue implements IBeanPropValue {
    public BeanMapValue() {

    }

    @Override
    public String getBeanValueType() {
        return "map";
    }

    @Override
    public void forEachChild(Consumer<IBeanPropValue> consumer) {
        this.getBody().forEach(entry -> {
            if (entry.getBody() != null)
                consumer.accept(entry.getBody());
        });
    }
}
