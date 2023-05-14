/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dataset.record.impl;

import io.nop.api.core.util.Guard;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordResourceMeta;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * 保持skip, readAll等函数的缺省实现。 一般在DelegateRecordInput的派生类中会增加处理逻辑，导致不能直接调用input上的skip等方法
 *
 * @param <T>
 */
public class DelegateRecordInput<T> implements IRecordInput<T> {
    protected final IRecordInput<T> input;

    public DelegateRecordInput(IRecordInput<T> input) {
        this.input = Guard.notNull(input, "input");
    }

    @Override
    public IRecordResourceMeta getMeta() {
        return input.getMeta();
    }

    public IRecordInput<T> getInput() {
        return input;
    }

    @Override
    public long getTotalCount() {
        return input.getTotalCount();
    }

    @Override
    public long getRemainingCount() {
        return input.getRemainingCount();
    }

    @Override
    public long skip(long count) {
        return input.skip(count);
    }

    @Override
    public IRecordInput<T> limit(long maxCount) {
        return input.limit(maxCount);
    }

    @Nonnull
    @Override
    public List<T> readBatch(int maxCount) {
        return input.readBatch(maxCount);
    }

    @Override
    public void readBatch(int maxCount, Consumer<T> ret) {
        input.readBatch(maxCount, ret);
    }

    @Nonnull
    @Override
    public List<T> readAll() {
        return input.readAll();
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    @Override
    public boolean hasNext() {
        return input.hasNext();
    }

    @Override
    public T next() {
        return input.next();
    }

    @Override
    public long getReadCount() {
        return input.getReadCount();
    }

    @Override
    public void remove() {
        input.remove();
    }
}