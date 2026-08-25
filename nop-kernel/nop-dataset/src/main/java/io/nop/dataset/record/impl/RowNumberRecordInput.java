/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record.impl;

import io.nop.commons.mutable.MutableLong;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRowNumberRecord;
import io.nop.dataset.record.SimpleRowNumberRecord;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * 在读取的数据记录对象上增加行号信息。 如果数据记录对象没有实现ILineRecord接口，则把它包装为SimpleLineRecord对象，从而确保行号信息可以保存到返回对象上。
 *
 * <p>类型约束：当记录未实现 {@link IRowNumberRecord} 时，本类的 {@code adapt} 会以
 * {@link SimpleRowNumberRecord} 替换原记录对象。由于泛型擦除无法在构造期校验，
 * 调用方必须以 {@code Object} 或 {@code IRowNumberRecord} 类型消费返回结果；
 * 若将 T 声明为具体业务类型，包装后的对象在按 T 使用时会抛 ClassCastException，
 * 报错位置远离根因。
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

    protected T adapt(T record, long readCount) {
        if (record instanceof IRowNumberRecord) {
            ((IRowNumberRecord) record).setRecordRowNumber(readCount);
        } else {
            record = (T) new SimpleRowNumberRecord(readCount, record);
        }
        return record;
    }
}