/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
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
 *
 * <p>退役代际契约（plan 2504 裁定①）：resize 收缩/close 只发布新代并对被裁剪旧代做退役登记
 * （宽限期内已交付资源保持存活，排空后统一关闭），get 热路径维持无锁快照零改动。
 * 构造失败路径维持 inline close（对象未发布、无并发读者，fail-fast 语义更优）。
 */
public class RoundRobinSupplier<T extends AutoCloseable> implements AutoCloseable, Supplier<T> {

    private final Supplier<T> factory;
    private volatile AtomicReferenceArray<T> objects; //NOSONAR
    private final AtomicInteger nextIndex = new AtomicInteger();

    public RoundRobinSupplier(Supplier<T> factory, int size) {
        this.factory = factory;
        this.objects = new AtomicReferenceArray<>(size);
        try {
            for (int i = 0; i < size; i++) {
                this.objects.set(i, factory.get());
            }
        } catch (Exception e) {
            closeInlineAll(this.objects);
            throw NopException.adapt(e);
        }
    }

    @Override
    public T get() {
        AtomicReferenceArray<T> objects = this.objects;
        int index = Math.abs(nextIndex.getAndIncrement() % objects.length());
        return objects.get(index);
    }

    public void resize(int size) {
        Guard.positiveInt(size, "cache size");

        AtomicReferenceArray<T> toRetire = null;
        synchronized (this) {
            AtomicReferenceArray<T> objects = this.objects;
            if (objects.length() > size) {
                AtomicReferenceArray<T> array = new AtomicReferenceArray<>(size);
                for (int i = 0; i < size; i++) {
                    array.set(i, objects.get(i));
                }
                // 缩小：发布新数组后，被裁剪尾段退役登记（延迟关闭），消除收缩窗 × 并发 get 的 use-after-close
                this.objects = array;
                toRetire = objects;
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
                    // 扩容失败时只关闭本次新建的资源（从旧数组长度开始），
                    // 从旧数组复制来的资源仍归旧数组管理。注意不能使用closeNext(newObjects, size)，
                    // 那样起始下标即终点，一个资源都不会被关闭。
                    // 本次新建资源从未交付（无读者），维持 inline close（fail-fast 语义）
                    closeNext(newObjects, objects.length());
                    throw NopException.adapt(e);
                }
                this.objects = newObjects;
            }
        }
        // 锁外退役登记：发布动作保护后发读者，宽限期保护发布前已捕获旧代快照的在飞读者
        if (toRetire != null)
            retireTail(toRetire, size);
    }

    private void closeNext(AtomicReferenceArray<T> array, int index) {
        for (int i = index; i < array.length(); i++) {
            IoHelper.safeClose(array.get(i));
        }
    }

    /**
     * 被裁剪尾段逐元素退役登记（关闭动作唯一执行位 = ResourceRetirementQueue 排空）
     */
    private void retireTail(AtomicReferenceArray<T> array, int fromIndex) {
        for (int i = fromIndex; i < array.length(); i++) {
            T resource = array.get(i);
            if (resource != null) {
                ResourceRetirementQueue.global().retire(resource, IoHelper::safeClose);
            }
        }
    }

    @Override
    public void close() {
        AtomicReferenceArray<T> objects = this.objects;
        if (objects != null) {
            // 整代退役（C1-001 F3③ 同族面）：与收缩同一机制，消除 close × 并发 get 的同型窗
            for (int i = 0, n = objects.length(); i < n; i++) {
                T resource = objects.get(i);
                if (resource != null) {
                    ResourceRetirementQueue.global().retire(resource, IoHelper::safeCloseObject);
                }
            }
        }
    }

    private static <T extends AutoCloseable> void closeInlineAll(AtomicReferenceArray<T> array) {
        for (int i = 0, n = array.length(); i < n; i++) {
            IoHelper.safeCloseObject(array.get(i));
        }
    }
}