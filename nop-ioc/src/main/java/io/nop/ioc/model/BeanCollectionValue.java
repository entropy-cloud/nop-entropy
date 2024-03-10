/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ioc.model;

import io.nop.ioc.model._gen._BeanCollectionValue;

import java.util.List;
import java.util.function.Consumer;

public abstract class BeanCollectionValue extends _BeanCollectionValue implements IBeanPropValue {
    public BeanCollectionValue() {

    }

    @Override
    public void forEachChild(Consumer<IBeanPropValue> consumer) {
        List<IBeanPropValue> items = this.getBody();
        if (items != null) {
            for (IBeanPropValue item : items) {
                consumer.accept(item);
            }
        }
    }
}
