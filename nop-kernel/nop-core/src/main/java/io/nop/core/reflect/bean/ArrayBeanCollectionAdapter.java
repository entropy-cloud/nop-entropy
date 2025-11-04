/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.reflect.bean;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.ObjIntConsumer;

public class ArrayBeanCollectionAdapter implements IBeanCollectionAdapter {
    public static final ArrayBeanCollectionAdapter INSTANCE = new ArrayBeanCollectionAdapter();

    @Override
    public int getSize(Object bean) {
        return Array.getLength(bean);
    }

    @Override
    public Class<?> getComponentType(Object bean) {
        return bean.getClass().getComponentType();
    }

    public Object getItem(Object bean, int index) {
        return Array.get(bean, index);
    }

    public void setItem(Object bean, int index, Object value) {
        Array.set(bean, index, value);
    }

    @Override
    public void forEach(Object bean, ObjIntConsumer<Object> action) {
        for (int i = 0, n = getSize(bean); i < n; i++) {
            Object value = getItem(bean, i);
            action.accept(value, i);
        }
    }

    @Override
    public Iterator<Object> iterator(Object bean) {
        return new Iterator<Object>() {
            int i = 0;
            int size = getSize(bean);

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public Object next() {
                if (!hasNext())
                    throw new NoSuchElementException();

                Object value = getItem(bean, i);
                i++;
                return value;
            }
        };
    }
}