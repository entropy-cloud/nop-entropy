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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;

/**
 * 缓存一组资源对象，通过RoundRobin的方式获取
 */
public class RoundRobinSupplier<T extends AutoCloseable> implements AutoCloseable, Supplier<T> {

    private final Supplier<T> factory;
    private volatile AtomicReferenceArray<T> objects;
    private final AtomicInteger nextIndex = new AtomicInteger();

    public RoundRobinSupplier(Supplier<T> factory, int size) {
        this.factory = factory;
        this.objects = new AtomicReferenceArray<>(size);
        try {
            for (int i = 0; i < size; i++) {
                this.objects.set(i, factory.get());
            }
        } catch (Exception e) {
            close();
            throw NopException.adapt(e);
        }
    }

    @Override
    public T get() {
        AtomicReferenceArray<T> objects = this.objects;
        int index = Math.abs(nextIndex.getAndIncrement() % objects.length());
        return objects.get(index);
    }

    public synchronized void resize(int size) {
        Guard.positiveInt(size, "cache size");

        AtomicReferenceArray<T> objects = this.objects;
        if (objects.length() > size) {
            AtomicReferenceArray<T> array = new AtomicReferenceArray<>(size);
            for (int i = 0; i < size; i++) {
                array.set(i, objects.get(i));
            }
            // 缩小
            this.objects = array;
            closeNext(objects, size);
        } else if (objects.length() < size) {
            // 扩大
            AtomicReferenceArray<T> newObjects = new AtomicReferenceArray<>(size);
            try {
                for (int i = 0; i < objects.length(); i++) {
                    newObjects.set(i, objects.get(i));
                }

                for (int i = objects.length(); i < size; i++) {
                    newObjects.set(i, factory.get());
                }
            } catch (Exception e) {
                closeNext(newObjects, size);
                throw NopException.adapt(e);
            }
            this.objects = newObjects;
        }
    }

    private void closeNext(AtomicReferenceArray<T> array, int index) {
        for (int i = index; i < array.length(); i++) {
            IoHelper.safeClose(array.get(i));
        }
    }

    @Override
    public void close() {
        AtomicReferenceArray<T> objects = this.objects;
        if (objects != null) {
            for (int i = 0, n = objects.length(); i < n; i++) {
                IoHelper.safeCloseObject(objects.get(i));
            }
        }
    }
}