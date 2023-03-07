/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.record.impl;

import io.nop.commons.mutable.MutableLong;
import io.nop.commons.record.IRecordInput;
import io.nop.commons.record.IRowNumberRecord;
import io.nop.commons.record.SimpleRowNumberRecord;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * 在读取的数据记录对象上增加行号信息。 如果数据记录对象没有实现ILineRecord接口，则把它包装为SimpleLineRecord对象，从而确保行号信息可以保存到返回对象上。
 *
 * @param <T> 数据记录类型
 */
public class RowNumberRecordInput<T> extends DelegateRecordInput<T> {
    public RowNumberRecordInput(IRecordInput<T> input) {
        super(input);
    }

    @Override
    public T next() {
        T record = super.next();
        if (record == null)
            return null;
        return adapt(record, getReadCount());
    }

    @Nonnull
    @Override
    public List<T> readBatch(int maxCount) {
        long readCount = getReadCount();
        List<T> list = input.readBatch(maxCount);
        adaptList(list, readCount);
        return list;
    }

    @Override
    public void readBatch(int maxCount, Consumer<T> ret) {
        MutableLong readCount = new MutableLong(getReadCount());
        input.readBatch(maxCount, item -> ret.accept(adapt(item, readCount.incrementAndGet())));
    }

    @Nonnull
    @Override
    public List<T> readAll() {
        long readCount = getReadCount();
        List<T> list = input.readAll();
        adaptList(list, readCount);
        return list;
    }

    private void adaptList(List<T> list, long readCount) {
        for (int i = 0, n = list.size(); i < n; i++) {
            T record = list.get(i);
            T adapted = adapt(record, ++readCount);
            if (adapted != record)
                list.set(i, adapted);
        }
    }

    private T adapt(T record, long readCount) {
        if (record instanceof IRowNumberRecord) {
            ((IRowNumberRecord) record).setRecordRowNumber(readCount);
        } else {
            record = (T) new SimpleRowNumberRecord(readCount, record);
        }
        return record;
    }
}