/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.concurrent;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.IoHelper;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 缓存一组资源对象，通过RoundRobin的方式获取
 */
public class RoundRobinSupplier<T extends AutoCloseable> implements AutoCloseable, Supplier<T> {

    private final Supplier<T> factory;
    private volatile Object[] objects;
    private final AtomicInteger nextIndex = new AtomicInteger();

    public RoundRobinSupplier(Supplier<T> factory, int size) {
        this.factory = factory;
        this.objects = new Object[size];
        try {
            for (int i = 0; i < size; i++) {
                this.objects[i] = factory.get();
            }
        } catch (Exception e) {
            close();
            throw NopException.adapt(e);
        }
    }

    @Override
    public T get() {
        Object[] objects = this.objects;
        int index = Math.abs(nextIndex.getAndIncrement() % objects.length);
        return (T) objects[index];
    }

    public synchronized void resize(int size) {
        Guard.positiveInt(size, "cache size");

        Object[] objects = this.objects;
        if (objects.length > size) {
            // 缩小
            this.objects = Arrays.copyOf(objects, size);
            closeNext(objects, size);
        } else if (objects.length < size) {
            // 扩大
            Object[] newObjects = Arrays.copyOf(objects, size);
            try {
                for (int i = objects.length; i < size; i++) {
                    newObjects[i] = factory.get();
                }
            } catch (Exception e) {
                closeNext(newObjects, size);
                throw NopException.adapt(e);
            }
        }
    }

    private void closeNext(Object[] array, int index) {
        for (int i = index; i < array.length; i++) {
            IoHelper.safeClose(array[i]);
        }
    }

    @Override
    public void close() {
        Object[] objects = this.objects;
        if (objects != null) {
            for (Object o : objects) {
                IoHelper.safeClose(o);
            }
        }
    }
}