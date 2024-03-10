/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import io.nop.core.reflect.ReflectionManager;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public class BeanCollectionAdapter implements IBeanCollectionAdapter {
    public static final BeanCollectionAdapter INSTANCE = new BeanCollectionAdapter();

    @Override
    public int getSize(Object bean) {
        return ((Collection) bean).size();
    }

    @Override
    public Class<?> getComponentType(Object bean) {
        return ReflectionManager.instance().buildGenericType(bean.getClass()).getComponentType().getRawClass();
    }

    @Override
    public void forEach(Object bean, ObjIntConsumer<Object> action) {
        ((Collection<Object>) bean).forEach(new Consumer<>() {
            private int i = 0;

            public void accept(Object item) {
                action.accept(item, i++);
            }
        });
    }

    @Override
    public Iterator<Object> iterator(Object bean) {
        return ((Collection<Object>) bean).iterator();
    }
}
